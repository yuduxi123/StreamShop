import { Router, Request, Response } from 'express';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { v4 as uuidv4 } from 'uuid';

interface VideoData {
  id: string;
  title: string;
  coverUrl: string;
  videoUrl: string;
  authorId: string;
  status: string;
  viewCount: number;
  likeCount: number;
  commentCount: number;
  shareCount: number;
  tags: string[];
  createdAt: string;
}

interface VideoProduct {
  id: string;
  videoId: string;
  productId: string;
  displayOrder: number;
}

const videoStorage = new StorageService<VideoData>('videos.json');
const vpStorage = new StorageService<VideoProduct>('video_products.json');
const productStorage = new StorageService<any>('products.json');
const userStorage = new StorageService<any>('users.json');

const router = Router();

// GET /api/videos - paginated feed, optional authorId filter
router.get('/', (req: Request, res: Response) => {
  const page = parseInt(req.query.page as string) || 1;
  const limit = parseInt(req.query.limit as string) || 10;
  const authorId = req.query.authorId as string | undefined;
  let result;
  if (authorId) {
    const filtered = videoStorage.query(v => v.authorId === authorId);
    const total = filtered.length;
    const start = (page - 1) * limit;
    result = { data: filtered.slice(start, start + limit), total, page, limit };
  } else {
    result = videoStorage.paginate(page, limit);
  }

  // Enrich with author and products
  const enriched = result.data.map(video => {
    const users = userStorage.query(u => u.id === video.authorId);
    const author = users.length > 0 ? { id: users[0].id, username: users[0].username, avatarUrl: users[0].avatarUrl } : null;
    const bindings = vpStorage.query(vp => vp.videoId === video.id);
    const products = bindings.map(b => productStorage.findById(b.productId)).filter(Boolean);
    return { ...video, author, products };
  });

  res.json({ data: enriched, total: result.total, page: result.page, limit: result.limit });
});

// GET /api/videos/:id
router.get('/:id', (req: Request, res: Response) => {
  const video = videoStorage.findById(req.params.id as string);
  if (!video) {
    res.status(404).json({ error: 'Video not found' });
    return;
  }
  const users = userStorage.query(u => u.id === video.authorId);
  const author = users.length > 0 ? { id: users[0].id, username: users[0].username, avatarUrl: users[0].avatarUrl } : null;
  const bindings = vpStorage.query(vp => vp.videoId === video.id);
  const products = bindings.map(b => productStorage.findById(b.productId)).filter(Boolean);
  res.json({ ...video, author, products });
});

// POST /api/videos
router.post('/', authMiddleware, (req: AuthRequest, res: Response) => {
  const { title, tags, status: reqStatus } = req.body;
  const coverUrl = req.body.coverUrl ?? req.body.cover_url;
  const videoUrl = req.body.videoUrl ?? req.body.video_url;
  if (!title || !videoUrl) {
    res.status(400).json({ error: 'Title and videoUrl required' });
    return;
  }
  const validStatuses = ['draft', 'published', 'taken_down'];
  const status = validStatuses.includes(reqStatus) ? reqStatus : 'draft';
  const video: VideoData = {
    id: uuidv4(),
    title,
    coverUrl: coverUrl || '',
    videoUrl,
    authorId: req.user!.id,
    status,
    viewCount: 0,
    likeCount: 0,
    commentCount: 0,
    shareCount: 0,
    tags: tags || [],
    createdAt: new Date().toISOString(),
  };
  videoStorage.create(video);
  res.status(201).json(video);
});

// PATCH /api/videos/:id
router.patch('/:id', authMiddleware, (req: AuthRequest, res: Response) => {
  const id = req.params.id as string;
  const existing = videoStorage.findById(id);
  if (!existing) {
    res.status(404).json({ error: 'Video not found' });
    return;
  }
  if (existing.authorId !== req.user!.id && req.user!.role !== 'admin') {
    res.status(403).json({ error: 'Forbidden' });
    return;
  }
  const updates: any = { ...req.body };
  if (updates.cover_url !== undefined) {
    updates.coverUrl = updates.cover_url;
    delete updates.cover_url;
  }
  if (updates.video_url !== undefined) {
    updates.videoUrl = updates.video_url;
    delete updates.video_url;
  }
  const updated = videoStorage.update(id, updates);
  if (!updated) {
    res.status(404).json({ error: 'Video not found' });
    return;
  }
  res.json(updated);
});

// DELETE /api/videos/:id
router.delete('/:id', authMiddleware, (req: AuthRequest, res: Response) => {
  const id = req.params.id as string;
  const existing = videoStorage.findById(id);
  if (!existing) {
    res.status(404).json({ error: 'Video not found' });
    return;
  }
  if (existing.authorId !== req.user!.id && req.user!.role !== 'admin') {
    res.status(403).json({ error: 'Forbidden' });
    return;
  }
  const deleted = videoStorage.delete(id);
  if (!deleted) {
    res.status(404).json({ error: 'Video not found' });
    return;
  }
  // Clean up bindings
  const bindings = vpStorage.query(vp => vp.videoId === id);
  bindings.forEach(b => vpStorage.delete(b.videoId + '_' + b.productId));
  res.status(204).send();
});

// POST /api/videos/:id/view - increment view count
router.post('/:id/view', (req: Request, res: Response) => {
  const video = videoStorage.findById(req.params.id as string);
  if (!video) {
    res.status(404).json({ error: 'Video not found' });
    return;
  }
  videoStorage.update(req.params.id as string, { viewCount: video.viewCount + 1 });
  res.json({ viewCount: video.viewCount + 1 });
});

export default router;
