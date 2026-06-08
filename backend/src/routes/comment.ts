import { Router, Request, Response } from 'express';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { v4 as uuidv4 } from 'uuid';
import { enrichComment } from './comment.logic';

interface CommentData {
  id: string;
  userId: string;
  targetType: string;
  targetId: string;
  content: string;
  likeCount: number;
  rating?: number;
  orderId?: string;
  createdAt: string;
}

const commentStorage = new StorageService<CommentData>('comments.json');
const userStorage = new StorageService<any>('users.json');
const videoStorage = new StorageService<any>('videos.json');
const productStorage = new StorageService<any>('products.json');

const router = Router();

// GET /api/comments?targetType=video&targetId=xxx&page=1&hasRating=true
router.get('/', (req: Request, res: Response) => {
  const { targetType, targetId } = req.query;
  const page = parseInt(req.query.page as string) || 1;
  const limit = parseInt(req.query.limit as string) || 20;
  const hasRating = req.query.hasRating === 'true';

  if (!targetType && !hasRating) {
    res.status(400).json({ error: 'targetType and targetId required' });
    return;
  }

  let comments: CommentData[];
  if (targetType && targetId) {
    comments = commentStorage.query(c => c.targetType === targetType && c.targetId === targetId);
  } else {
    comments = commentStorage.findAll();
  }

  if (hasRating) {
    comments = comments.filter(c => (c.rating ?? 0) > 0);
  }

  comments.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

  const total = comments.length;
  const start = (page - 1) * limit;
  const paged = comments.slice(start, start + limit);

  const users = userStorage.findAll();
  const enriched = paged.map(comment => enrichComment(comment, users));

  res.json({ data: enriched, total, page, limit });
});

// POST /api/comments
router.post('/', authMiddleware, (req: AuthRequest, res: Response) => {
  const { targetType, targetId, content, rating, orderId } = req.body;
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
    rating: rating ?? 0,
    orderId: orderId ?? undefined,
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

  res.status(201).json(enrichComment(comment, userStorage.findAll()));
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
