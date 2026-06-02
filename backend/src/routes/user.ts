import { Router, Request, Response } from 'express';
import { AuthService } from '../services/auth.service';
import { StorageService } from '../services/storage.service';

interface VideoData {
  id: string; authorId: string; likeCount: number;
}
const videoStorage = new StorageService<VideoData>('videos.json');

const router = Router();

// GET /api/users/:id - public user profile
router.get('/:id', (req: Request, res: Response) => {
  const userId = req.params.id as string;
  const user = AuthService.getUserById(userId);
  if (!user) {
    res.status(404).json({ error: 'User not found' });
    return;
  }
  const userVideos = videoStorage.query(v => v.authorId === userId);
  const likesReceived = userVideos.reduce((s, v) => s + (v.likeCount || 0), 0);
  res.json({ ...user, likesReceived });
});

export default router;
