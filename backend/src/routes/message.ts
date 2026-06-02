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
  status: 'sent' | 'delivered' | 'read';
  createdAt: string;
}

const messageStorage = new StorageService<MessageData>('messages.json');
const router = Router();

function getConversationId(a: string, b: string): string {
  return [a, b].sort().join('_');
}

// GET /api/messages/conversations — list user's conversations
router.get('/conversations', authMiddleware, (req: AuthRequest, res: Response) => {
  const userId = req.user!.id;
  const allMessages = messageStorage.findAll();
  const conversationMap = new Map<string, { lastMessage: string; lastMessageAt: string; unreadCount: number }>();

  for (const msg of allMessages) {
    if (msg.senderId !== userId && msg.receiverId !== userId) continue;
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

  const conversations = [];
  for (const [convId, data] of conversationMap.entries()) {
    const participants = convId.split('_');
    const otherId = participants[0] === userId ? participants[1] : participants[0];
    const otherUser = AuthService.getUserById(otherId);
    conversations.push({
      conversationId: convId,
      otherUser: otherUser ? { id: otherUser.id, username: otherUser.username, avatarUrl: otherUser.avatarUrl } : null,
      lastMessage: data.lastMessage,
      lastMessageAt: data.lastMessageAt,
      unreadCount: data.unreadCount,
    });
  }

  conversations.sort((a, b) => b.lastMessageAt.localeCompare(a.lastMessageAt));
  res.json(conversations);
});

// GET /api/messages/conversations/:id — get messages in a conversation
router.get('/conversations/:id', authMiddleware, (req: AuthRequest, res: Response) => {
  const userId = req.user!.id;
  const convId = req.params.id as string;
  const participants = convId.split('_');
  if (!participants.includes(userId)) {
    res.status(403).json({ error: 'Not a participant' });
    return;
  }

  const page = parseInt(req.query.page as string) || 1;
  const limit = parseInt(req.query.limit as string) || 50;
  const all = messageStorage.query(m => m.conversationId === convId);
  all.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

  const total = all.length;
  const start = (page - 1) * limit;
  const data = all.slice(start, start + limit).reverse();

  // Mark incoming messages as read
  for (const msg of all) {
    if (msg.receiverId === userId && msg.status !== 'read') {
      messageStorage.update(msg.id, { status: 'read' });
    }
  }

  res.json({ data, total, page, limit });
});

// POST /api/messages — send a message
router.post('/', authMiddleware, (req: AuthRequest, res: Response) => {
  const userId = req.user!.id;
  const username = req.user!.username;
  const { receiverId, content } = req.body;

  if (!receiverId || !content || !content.trim()) {
    res.status(400).json({ error: 'receiverId and content are required' });
    return;
  }

  const receiver = AuthService.getUserById(receiverId);
  if (!receiver) {
    res.status(404).json({ error: 'Receiver not found' });
    return;
  }

  const msg: MessageData = {
    id: uuidv4(),
    conversationId: getConversationId(userId, receiverId),
    senderId: userId,
    receiverId,
    content: content.trim(),
    status: 'sent',
    createdAt: new Date().toISOString(),
  };

  messageStorage.create(msg);

  // Push via WebSocket to receiver if online
  const wss = (req.app as any).locals?.wss;
  if (wss?.pushMessage) {
    wss.pushMessage(receiverId, {
      type: 'NEW_MESSAGE',
      message: {
        ...msg,
        senderUsername: username,
      },
    });
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
