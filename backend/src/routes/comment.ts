import { Router, Request, Response } from 'express';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { v4 as uuidv4 } from 'uuid';

interface CommentData {
  id: string;
  userId: string;
  targetType: string;
  targetId: string;
  content: string;
  likeCount: number;
  createdAt: string;
}

const commentStorage = new StorageService<CommentData>('comments.json');
const userStorage = new StorageService<any>('users.json');
const videoStorage = new StorageService<any>('videos.json');
const productStorage = new StorageService<any>('products.json');

const router = Router();

// GET /api/comments?targetType=video&targetId=xxx&page=1
router.get('/', (req: Request, res: Response) => {
  const { targetType, targetId } = req.query;
  const page = parseInt(req.query.page as string) || 1;
  const limit = parseInt(req.query.limit as string) || 20;

  if (!targetType || !targetId) {
    res.status(400).json({ error: 'targetType and targetId required' });
    return;
  }

  let comments = commentStorage.query(c => c.targetType === targetType && c.targetId === targetId);
  comments.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

  const total = comments.length;
  const start = (page - 1) * limit;
  const paged = comments.slice(start, start + limit);

  const enriched = paged.map(comment => {
    const users = userStorage.query(u => u.id === comment.userId);
    const user = users.length > 0 ? { id: users[0].id, username: users[0].username, avatarUrl: users[0].avatarUrl } : null;
    return { ...comment, user };
  });

  res.json({ data: enriched, total, page, limit });
});

// POST /api/comments
router.post('/', authMiddleware, (req: AuthRequest, res: Response) => {
  const { targetType, targetId, content } = req.body;
  if (!targetType || !targetId || !content) {
    res.status(400).json({ error: 'targetType, targetId, content required' });
    return;
  }

  const comment: CommentData = {
    id: uuidv4(),
    userId: req.user!.id,
    targetType,
    targetId,
    content,
    likeCount: 0,
    createdAt: new Date().toISOString(),
  };
  commentStorage.create(comment);

  // Increment commentCount on the target
  if (targetType === 'video') {
    const video = videoStorage.findById(targetId);
    if (video) {
      videoStorage.update(targetId, { commentCount: (video.commentCount || 0) + 1 });
    }
  } else if (targetType === 'product') {
    const product = productStorage.findById(targetId);
    if (product) {
      productStorage.update(targetId, { commentCount: (product.commentCount || 0) + 1 });
    }
  }

  res.status(201).json(comment);
});

// DELETE /api/comments/:id
router.delete('/:id', authMiddleware, (req: AuthRequest, res: Response) => {
  const comment = commentStorage.findById(req.params.id as string);
  if (!comment) {
    res.status(404).json({ error: 'Comment not found' });
    return;
  }
  if (comment.userId !== req.user!.id) {
    res.status(403).json({ error: 'Not authorized' });
    return;
  }
  commentStorage.delete(req.params.id as string);
  res.status(204).send();
});

export default router;
