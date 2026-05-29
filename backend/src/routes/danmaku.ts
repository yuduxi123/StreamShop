import { Router, Response } from 'express';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { v4 as uuidv4 } from 'uuid';

interface DanmakuData {
  id: string;
  videoId: string;
  userId: string;
  username: string;
  content: string;
  color: string;
  timestampMs: number;
  createdAt: string;
}

const danmakuStorage = new StorageService<DanmakuData>('danmakus.json');
const userStorage = new StorageService<any>('users.json');

const router = Router();

// GET /api/danmaku?videoId=xxx — load all danmaku for a video, sorted by timestamp
router.get('/', (req: AuthRequest, res: Response) => {
  const videoId = req.query.videoId as string;
  if (!videoId) {
    res.status(400).json({ error: 'videoId required' });
    return;
  }

  const items = danmakuStorage.query(d => d.videoId === videoId);
  items.sort((a, b) => a.timestampMs - b.timestampMs);

  const enriched = items.map(item => {
    const users = userStorage.query(u => u.id === item.userId);
    const user = users.length > 0 ? { id: users[0].id, username: users[0].username, avatarUrl: users[0].avatarUrl } : null;
    return { ...item, user };
  });

  res.json(enriched);
});

// POST /api/danmaku — create a danmaku and push via WebSocket
router.post('/', authMiddleware, (req: AuthRequest, res: Response) => {
  const { videoId, content, color, timestampMs } = req.body;
  if (!videoId || !content) {
    res.status(400).json({ error: 'videoId and content required' });
    return;
  }

  const danmaku: DanmakuData = {
    id: uuidv4(),
    videoId,
    userId: req.user!.id,
    username: req.user!.username,
    content,
    color: color || '#FFFFFF',
    timestampMs: timestampMs || 0,
    createdAt: new Date().toISOString(),
  };

  danmakuStorage.create(danmaku);

  // Push to connected WebSocket clients watching this video
  const wss = req.app.locals.wss;
  if (wss) {
    wss.pushDanmaku('video_' + videoId, danmaku);
  }

  res.status(201).json(danmaku);
});

export default router;
