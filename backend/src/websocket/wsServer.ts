import { Server as HttpServer } from 'http';
import { WebSocketServer as WSS, WebSocket } from 'ws';
import { v4 as uuidv4 } from 'uuid';

interface RoomMember {
  ws: WebSocket;
  userId: string;
  username: string;
}

const rooms = new Map<string, Map<WebSocket, RoomMember>>();
const hotValues = new Map<string, number>();
const userSockets = new Map<string, WebSocket>();

export class WebSocketServer {
  private wss: WSS;

  constructor(server: HttpServer) {
    this.wss = new WSS({ server, path: '/ws' });
    this.setup();

    // Decay hot values every 30s
    setInterval(() => {
      for (const [roomId, value] of hotValues.entries()) {
        const decayed = Math.max(0, value - Math.floor(value * 0.15));
        hotValues.set(roomId, decayed);
        this.broadcastToRoom(roomId, { type: 'HOT_VALUE_UPDATE', roomId, value: decayed });
      }
    }, 30000);
  }

  private setup(): void {
    this.wss.on('connection', (ws: WebSocket) => {
      ws.on('message', (raw: Buffer) => {
        try {
          const msg = JSON.parse(raw.toString());
          this.handleMessage(ws, msg);
        } catch { /* ignore invalid JSON */ }
      });

      ws.on('close', () => {
        this.handleDisconnect(ws);
      });

      // Send initial pong
      this.send(ws, { type: 'CONNECTED', timestamp: new Date().toISOString() });
    });
  }

  private handleMessage(ws: WebSocket, msg: any): void {
    switch (msg.type) {
      case 'JOIN_ROOM':
        this.joinRoom(ws, msg.roomId, msg.userId, msg.username);
        break;
      case 'LEAVE_ROOM':
        this.leaveRoom(ws, msg.roomId);
        break;
      case 'SEND_COMMENT':
        this.handleComment(ws, msg);
        break;
      case 'SEND_DANMAKU':
        this.handleDanmaku(ws, msg);
        break;
      case 'LIKE':
        this.handleLike(ws, msg);
        break;
      case 'CLAIM_COUPON':
        this.broadcastToRoom(msg.roomId, msg, ws);
        break;
      case 'PING':
        this.send(ws, { type: 'PONG' });
        break;
      case 'AUTHENTICATE':
        this.handleAuthenticate(ws, msg.userId);
        break;
      case 'GET_ROOM_USERS':
        this.handleGetRoomUsers(ws, msg.roomId);
        break;
    }
  }

  private handleComment(ws: WebSocket, msg: any): void {
    const enriched = {
      type: 'NEW_COMMENT',
      id: uuidv4(),
      userId: msg.userId,
      username: msg.username || '匿名用户',
      content: msg.content,
      timestamp: new Date().toISOString(),
    };
    this.broadcastToRoom(msg.roomId, enriched, ws);
    this.incrementHot(msg.roomId, 3);
  }

  private handleDanmaku(ws: WebSocket, msg: any): void {
    const enriched = {
      type: 'NEW_DANMAKU',
      id: uuidv4(),
      userId: msg.userId,
      username: msg.username || '匿名用户',
      content: msg.content,
      color: '#FFFFFF',
      timestamp: new Date().toISOString(),
    };
    this.broadcastToRoom(msg.roomId, enriched);
    this.incrementHot(msg.roomId, 2);
  }

  private handleLike(ws: WebSocket, msg: any): void {
    this.incrementHot(msg.roomId, 1);
    this.broadcastToRoom(msg.roomId, {
      type: 'LIKE_UPDATE',
      roomId: msg.roomId,
      userId: msg.userId,
      targetType: msg.targetType,
      targetId: msg.targetId,
    }, ws);
  }

  private incrementHot(roomId: string, amount: number): void {
    hotValues.set(roomId, (hotValues.get(roomId) || 0) + amount);
  }

  // --- Public API for REST routes to push events ---

  pushProductChanged(roomId: string, productId: string, action: 'added' | 'removed' | 'explaining' | 'reordered', productIds?: string[]): void {
    this.broadcastToRoom(roomId, {
      type: 'PRODUCT_CHANGED',
      roomId,
      productId,
      action,
      productIds: productIds || [],
    });
  }

  pushCoupon(roomId: string, coupon: any): void {
    this.broadcastToRoom(roomId, {
      type: 'COUPON_PUSHED',
      roomId,
      coupon,
    });
  }

  pushDanmaku(roomId: string, danmaku: any): void {
    this.broadcastToRoom(roomId, {
      type: 'NEW_DANMAKU',
      ...danmaku,
    });
  }

  pushLiveStarted(roomId: string): void {
    this.broadcastToAll({
      type: 'LIVE_STARTED',
      roomId,
    });
  }

  pushPurchase(roomId: string, purchase: { username: string; productTitle: string; quantity: number }): void {
    this.broadcastToRoom(roomId, {
      type: 'PURCHASE',
      ...purchase,
    });
  }

  pushMessage(targetUserId: string, message: any): void {
    const ws = userSockets.get(targetUserId);
    if (ws && ws.readyState === WebSocket.OPEN) {
      this.send(ws, message);
    }
  }

  // --- Internal ---

  private joinRoom(ws: WebSocket, roomId: string, userId: string, username: string): void {
    if (!roomId || !userId) return;
    if (!rooms.has(roomId)) {
      rooms.set(roomId, new Map());
    }
    const room = rooms.get(roomId)!;
    room.set(ws, { ws, userId, username });

    this.send(ws, { type: 'ROOM_JOINED', roomId, onlineCount: room.size, hotValue: hotValues.get(roomId) || 0 });
    this.broadcastToRoom(roomId, { type: 'ONLINE_COUNT_UPDATE', roomId, count: room.size }, ws);
    this.broadcastToRoom(roomId, { type: 'USER_JOINED', userId, username });
  }

  private leaveRoom(ws: WebSocket, roomId: string): void {
    if (!roomId) return;
    const room = rooms.get(roomId);
    if (!room) return;
    room.delete(ws);
    if (room.size === 0) {
      rooms.delete(roomId);
      hotValues.delete(roomId);
    } else {
      this.broadcastToRoom(roomId, { type: 'ONLINE_COUNT_UPDATE', roomId, count: room.size }, ws);
    }
  }

  private handleAuthenticate(ws: WebSocket, userId: string): void {
    if (!userId) return;
    userSockets.set(userId, ws);
    this.send(ws, { type: 'AUTHENTICATED', userId });
  }

  private handleGetRoomUsers(ws: WebSocket, roomId: string): void {
    const room = rooms.get(roomId);
    if (!room) {
      this.send(ws, { type: 'ROOM_USERS', roomId, users: [] });
      return;
    }
    const users: { userId: string; username: string }[] = [];
    for (const member of room.values()) {
      users.push({ userId: member.userId, username: member.username });
    }
    this.send(ws, { type: 'ROOM_USERS', roomId, users });
  }

  private handleDisconnect(ws: WebSocket): void {
    // Clean up room membership
    for (const [roomId, room] of rooms.entries()) {
      if (room.has(ws)) {
        room.delete(ws);
        if (room.size === 0) {
          rooms.delete(roomId);
          hotValues.delete(roomId);
        } else {
          this.broadcastToRoom(roomId, { type: 'ONLINE_COUNT_UPDATE', roomId, count: room.size }, ws);
        }
      }
    }
    // Clean up user socket
    for (const [userId, socket] of userSockets.entries()) {
      if (socket === ws) {
        userSockets.delete(userId);
        break;
      }
    }
  }

  private broadcastToRoom(roomId: string, message: any, exclude?: WebSocket): void {
    const room = rooms.get(roomId);
    if (!room) return;
    const data = JSON.stringify(message);
    for (const [client] of room) {
      if (client !== exclude && client.readyState === WebSocket.OPEN) {
        client.send(data);
      }
    }
  }

  private broadcastToAll(message: any): void {
    const data = JSON.stringify(message);
    this.wss.clients.forEach(client => {
      if (client.readyState === WebSocket.OPEN) {
        client.send(data);
      }
    });
  }

  private send(ws: WebSocket, message: any): void {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(message));
    }
  }
}
