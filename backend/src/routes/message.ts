import { Router, Request, Response } from 'express';
import { AuthService } from '../services/auth.service';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { v4 as uuidv4 } from 'uuid';

interface MessageData {
  id: string;
  conversationId: string;
  senderId: string;
  receiverId: string;
  content: string;
  type?: 'text' | 'forward';
  videoId?: string;
  status: 'sent' | 'delivered' | 'read';
  createdAt: string;
}

const messageStorage = new StorageService<MessageData>('messages.json');
const videoStorage = new StorageService<any>('videos.json');

interface GroupConversation {
  id: string;
  name: string;
  creatorId: string;
  memberIds: string[];
  createdAt: string;
}

const groupStorage = new StorageService<GroupConversation>('groups.json');
const router = Router();

function getConversationId(a: string, b: string): string {
  return [a, b].sort().join('_');
}

// POST /api/messages/conversations/group — create a group conversation
router.post('/conversations/group', authMiddleware, (req: AuthRequest, res: Response) => {
  const userId = req.user!.id;
  const { name, memberIds } = req.body;
  if (!name || !memberIds || !Array.isArray(memberIds) || memberIds.length < 1) {
    res.status(400).json({ error: 'name and memberIds (array with at least 1) are required' });
    return;
  }
  const allMemberIds = [...new Set([userId, ...memberIds])];
  const group: GroupConversation = {
    id: uuidv4(),
    name: name.trim(),
    creatorId: userId,
    memberIds: allMemberIds,
    createdAt: new Date().toISOString(),
  };
  groupStorage.create(group);
  res.json(group);
});

// GET /api/messages/conversations — list user's conversations
router.get('/conversations', authMiddleware, (req: AuthRequest, res: Response) => {
  const userId = req.user!.id;
  const allMessages = messageStorage.findAll();
  const conversationMap = new Map<string, { lastMessage: string; lastMessageAt: string; unreadCount: number }>();

  for (const msg of allMessages) {
    if (msg.senderId !== userId && msg.receiverId !== userId) continue;
    // Skip group messages — handled separately below
    if (msg.receiverId === 'group') continue;
    const isIncoming = msg.receiverId === userId;
    const existing = conversationMap.get(msg.conversationId);
    if (!existing || msg.createdAt > existing.lastMessageAt) {
      conversationMap.set(msg.conversationId, {
        lastMessage: msg.content,
        lastMessageAt: msg.createdAt,
        unreadCount: (existing?.unreadCount || 0) + (isIncoming && msg.status !== 'read' ? 1 : 0),
      });
    } else if (isIncoming && msg.status !== 'read') {
      existing.unreadCount++;
    }
  }

  // Also track the latest message metadata (type, videoId, orderId) per conversation
  const latestMsgMeta = new Map<string, { type?: string; videoId?: string; orderId?: string }>();
  for (const msg of allMessages) {
    if (msg.senderId !== userId && msg.receiverId !== userId) continue;
    if (msg.receiverId === 'group') continue;
    const existing = latestMsgMeta.get(msg.conversationId);
    if (!existing || msg.createdAt > (conversationMap.get(msg.conversationId)?.lastMessageAt || '')) {
      latestMsgMeta.set(msg.conversationId, { type: msg.type, videoId: msg.videoId, orderId: (msg as any).orderId });
    }
  }

  const conversations = [];
  for (const [convId, data] of conversationMap.entries()) {
    const participants = convId.split('_');
    const otherId = participants[0] === userId ? participants[1] : participants[0];
    const otherUser = AuthService.getUserById(otherId);
    const meta = latestMsgMeta.get(convId) || {};
    const entry: any = {
      conversationId: convId,
      otherUser: otherUser ? { id: otherUser.id, username: otherUser.username, avatarUrl: otherUser.avatarUrl } : null,
      lastMessage: data.lastMessage,
      lastMessageAt: data.lastMessageAt,
      lastMessageType: meta.type || 'text',
      lastMessageVideoId: meta.videoId || null,
      lastMessageOrderId: meta.orderId || null,
      unreadCount: data.unreadCount,
    };
    if (meta.type === 'forward' && meta.videoId) {
      const video = videoStorage.findById(meta.videoId);
      if (video) {
        entry.lastMessageVideoTitle = video.title;
        entry.lastMessageVideoCover = video.coverUrl;
      }
    }
    conversations.push(entry);
  }

  // Add group conversations
  const userGroups = groupStorage.query(g => g.memberIds.includes(userId));
  for (const group of userGroups) {
    const groupMessages = allMessages.filter(m => m.conversationId === group.id);
    groupMessages.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
    const lastMsg = groupMessages[0];
    const unreadCount = groupMessages.filter(m => m.senderId !== userId && m.status !== 'read').length;
    const memberAvatars = group.memberIds.slice(0, 4).map(mid => {
      const u = AuthService.getUserById(mid);
      return u ? u.avatarUrl : null;
    }).filter(Boolean);
    const entry: any = {
      conversationId: group.id,
      isGroup: true,
      groupName: group.name,
      memberAvatars,
      otherUser: null,
      lastMessage: lastMsg ? lastMsg.content : '',
      lastMessageAt: lastMsg ? lastMsg.createdAt : group.createdAt,
      lastMessageType: lastMsg?.type || 'text',
      lastMessageVideoId: lastMsg?.videoId || null,
      unreadCount,
    };
    if (lastMsg?.type === 'forward' && lastMsg.videoId) {
      const video = videoStorage.findById(lastMsg.videoId);
      if (video) {
        entry.lastMessageVideoTitle = video.title;
        entry.lastMessageVideoCover = video.coverUrl;
      }
    }
    conversations.push(entry);
  }

  conversations.sort((a, b) => b.lastMessageAt.localeCompare(a.lastMessageAt));
  res.json(conversations);
});

// GET /api/messages/conversations/:id — get messages in a conversation
router.get('/conversations/:id', authMiddleware, (req: AuthRequest, res: Response) => {
  const userId = req.user!.id;
  const convId = req.params.id as string;

  // Check if it's a group conversation
  const group = groupStorage.findById(convId);
  if (group) {
    if (!group.memberIds.includes(userId)) {
      res.status(403).json({ error: 'Not a member of this group' });
      return;
    }
  } else {
    const participants = convId.split('_');
    if (!participants.includes(userId)) {
      res.status(403).json({ error: 'Not a participant' });
      return;
    }
  }

  const page = parseInt(req.query.page as string) || 1;
  const limit = parseInt(req.query.limit as string) || 50;
  const all = messageStorage.query(m => m.conversationId === convId);
  all.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

  const total = all.length;
  const start = (page - 1) * limit;
  const data = all.slice(start, start + limit).reverse();

  // Enrich forward messages with video data, and add senderUsername
  const enriched = data.map(msg => {
    let result: any = { ...msg };
    if (msg.type === 'forward' && msg.videoId) {
      const video = videoStorage.findById(msg.videoId);
      if (video) {
        result.videoTitle = video.title;
        result.videoCoverUrl = video.coverUrl;
      }
    }
    if (msg.type === 'order_remind' && (msg as any).orderId) {
      result.orderId = (msg as any).orderId;
    }
    const sender = AuthService.getUserById(msg.senderId);
    if (sender) {
      result.senderUsername = sender.username;
    }
    return result;
  });

  // Mark incoming messages as read
  for (const msg of all) {
    const isIncoming = group
      ? (msg.senderId !== userId)
      : (msg.receiverId === userId);
    if (isIncoming && msg.status !== 'read') {
      messageStorage.update(msg.id, { status: 'read' });
    }
  }

  res.json({ data: enriched, total, page, limit });
});

// POST /api/messages — send a message (1-on-1 or group)
router.post('/', authMiddleware, (req: AuthRequest, res: Response) => {
  const userId = req.user!.id;
  const username = req.user!.username;
  const { receiverId, content, conversationId } = req.body;

  if (!content || !content.trim()) {
    res.status(400).json({ error: 'content is required' });
    return;
  }

  let convId: string;
  let recvId: string;
  let isGroup = false;

  if (conversationId) {
    const group = groupStorage.findById(conversationId);
    if (group) {
      if (!group.memberIds.includes(userId)) {
        res.status(403).json({ error: 'Not a member of this group' });
        return;
      }
      convId = conversationId;
      recvId = 'group';
      isGroup = true;
    } else {
      convId = conversationId;
      const parts = convId.split('_');
      recvId = parts[0] === userId ? parts[1] : parts[0];
    }
  } else if (receiverId) {
    const receiver = AuthService.getUserById(receiverId);
    if (!receiver) {
      res.status(404).json({ error: 'Receiver not found' });
      return;
    }
    recvId = receiverId;
    convId = getConversationId(userId, receiverId);
  } else {
    res.status(400).json({ error: 'receiverId or conversationId required' });
    return;
  }

  const msg: MessageData = {
    id: uuidv4(),
    conversationId: convId,
    senderId: userId,
    receiverId: recvId,
    content: content.trim(),
    status: 'sent',
    createdAt: new Date().toISOString(),
  };

  messageStorage.create(msg);

  // Push via WebSocket
  const wss = (req.app as any).locals?.wss;
  if (wss?.pushMessage) {
    if (isGroup) {
      const group = groupStorage.findById(convId)!;
      for (const memberId of group.memberIds) {
        if (memberId !== userId) {
          wss.pushMessage(memberId, {
            type: 'NEW_MESSAGE',
            message: { ...msg, senderUsername: username },
          });
        }
      }
    } else {
      wss.pushMessage(recvId, {
        type: 'NEW_MESSAGE',
        message: { ...msg, senderUsername: username },
      });
    }
  }

  res.json(msg);
});

// GET /api/messages/unread-count — count unread messages
router.get('/unread-count', authMiddleware, (req: AuthRequest, res: Response) => {
  const userId = req.user!.id;
  const count = messageStorage.query(m => m.receiverId === userId && m.status !== 'read').length;
  res.json({ count });
});

export default router;
