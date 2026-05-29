import { Router, Response } from 'express';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';

interface LikeData {
  id: string;
  userId: string;
  targetType: string;
  targetId: string;
  createdAt: string;
}

interface CollectionData {
  id: string;
  userId: string;
  targetType: string;
  targetId: string;
  createdAt: string;
}

const likeStorage = new StorageService<LikeData>('likes.json');
const collectionStorage = new StorageService<CollectionData>('collections.json');
const videoStorage = new StorageService<any>('videos.json');
const productStorage = new StorageService<any>('products.json');
const vpStorage = new StorageService<any>('video_products.json');
const userStorage = new StorageService<any>('users.json');

const router = Router();

// ---- LIKES ----

// POST /api/interactions/likes - toggle like
router.post('/likes', authMiddleware, (req: AuthRequest, res: Response) => {
  const { targetType, targetId } = req.body;
  if (!targetType || !targetId) {
    res.status(400).json({ error: 'targetType and targetId required' });
    return;
  }

  const existing = likeStorage.query(l => l.userId === req.user!.id && l.targetType === targetType && l.targetId === targetId);

  if (existing.length > 0) {
    // Unlike
    likeStorage.delete(existing[0].userId + '_' + existing[0].targetType + '_' + existing[0].targetId);
    updateCount(targetType, targetId, -1);
    res.json({ liked: false });
  } else {
    // Like
    const like: LikeData = {
      id: req.user!.id + '_' + targetType + '_' + targetId,
      userId: req.user!.id,
      targetType,
      targetId,
      createdAt: new Date().toISOString(),
    };
    likeStorage.create(like);
    updateCount(targetType, targetId, 1);
    res.json({ liked: true });
  }
});

// GET /api/interactions/likes?targetType=video - list liked items for current user
router.get('/likes', authMiddleware, (req: AuthRequest, res: Response) => {
  const targetType = req.query.targetType as string;
  let items = likeStorage.query(l => l.userId === req.user!.id);
  if (targetType) {
    items = items.filter(l => l.targetType === targetType);
  }
  // Sort by newest first
  items.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  // Enrich with video/product data, author and products
  const enriched = items.map(item => {
    let target: any = null;
    if (item.targetType === 'video') {
      const video = videoStorage.findById(item.targetId);
      if (video) {
        const users = userStorage.query(u => u.id === video.authorId);
        const author = users.length > 0 ? { id: users[0].id, username: users[0].username, avatarUrl: users[0].avatarUrl } : null;
        const bindings = vpStorage.query((vp: any) => vp.videoId === video.id);
        const products = bindings.map((b: any) => productStorage.findById(b.productId)).filter(Boolean);
        target = { ...video, author, products };
      }
    } else if (item.targetType === 'product') {
      target = productStorage.findById(item.targetId);
    }
    return { ...item, target };
  });
  res.json(enriched);
});

// GET /api/interactions/likes/status?targetType=video&targetId=xxx
router.get('/likes/status', authMiddleware, (req: AuthRequest, res: Response) => {
  const { targetType, targetId } = req.query;
  if (!targetType || !targetId) {
    res.status(400).json({ error: 'targetType and targetId required' });
    return;
  }
  const existing = likeStorage.query(l => l.userId === req.user!.id && l.targetType === targetType && l.targetId === targetId);
  res.json({ liked: existing.length > 0 });
});

// GET /api/interactions/likes/count?targetType=video&targetId=xxx
router.get('/likes/count', (req: AuthRequest, res: Response) => {
  const { targetType, targetId } = req.query;
  if (!targetType || !targetId) {
    res.status(400).json({ error: 'targetType and targetId required' });
    return;
  }
  const count = likeStorage.query(l => l.targetType === targetType && l.targetId === targetId).length;
  res.json({ count });
});

// ---- COLLECTIONS ----

router.post('/collections', authMiddleware, (req: AuthRequest, res: Response) => {
  const { targetType, targetId } = req.body;
  if (!targetType || !targetId) {
    res.status(400).json({ error: 'targetType and targetId required' });
    return;
  }

  const existing = collectionStorage.query(c => c.userId === req.user!.id && c.targetType === targetType && c.targetId === targetId);
  if (existing.length > 0) {
    collectionStorage.delete(existing[0].userId + '_' + existing[0].targetType + '_' + existing[0].targetId);
    updateCollectionCount(targetType, targetId, -1);
    res.json({ collected: false });
  } else {
    const collection: CollectionData = {
      id: req.user!.id + '_' + targetType + '_' + targetId,
      userId: req.user!.id,
      targetType,
      targetId,
      createdAt: new Date().toISOString(),
    };
    collectionStorage.create(collection);
    updateCollectionCount(targetType, targetId, 1);
    res.json({ collected: true });
  }
});

router.get('/collections/status', authMiddleware, (req: AuthRequest, res: Response) => {
  const { targetType, targetId } = req.query;
  if (!targetType || !targetId) {
    res.status(400).json({ error: 'targetType and targetId required' });
    return;
  }
  const existing = collectionStorage.query(c => c.userId === req.user!.id && c.targetType === targetType && c.targetId === targetId);
  res.json({ collected: existing.length > 0 });
});

// GET /api/interactions/collections?targetType=video
router.get('/collections', authMiddleware, (req: AuthRequest, res: Response) => {
  const targetType = req.query.targetType as string;
  let items = collectionStorage.query(c => c.userId === req.user!.id);
  if (targetType) {
    items = items.filter(c => c.targetType === targetType);
  }
  // Sort by newest first
  items.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  // Enrich with target data
  const enriched = items.map(item => {
    let target: any = null;
    if (item.targetType === 'video') {
      const video = videoStorage.findById(item.targetId);
      if (video) {
        const users = userStorage.query(u => u.id === video.authorId);
        const author = users.length > 0 ? { id: users[0].id, username: users[0].username, avatarUrl: users[0].avatarUrl } : null;
        const bindings = vpStorage.query((vp: any) => vp.videoId === video.id);
        const products = bindings.map((b: any) => productStorage.findById(b.productId)).filter(Boolean);
        target = { ...video, author, products };
      }
    } else if (item.targetType === 'product') {
      target = productStorage.findById(item.targetId);
    }
    return { ...item, target };
  });
  res.json(enriched);
});

function updateCount(targetType: string, targetId: string, delta: number): void {
  if (targetType === 'video') {
    const video = videoStorage.findById(targetId);
    if (video) {
      videoStorage.update(targetId, { likeCount: Math.max(0, video.likeCount + delta) });
    }
  } else if (targetType === 'product') {
    const product = productStorage.findById(targetId);
    if (product) {
      productStorage.update(targetId, { likeCount: Math.max(0, (product.likeCount || 0) + delta) });
    }
  }
}

function updateCollectionCount(targetType: string, targetId: string, delta: number): void {
  if (targetType === 'video') {
    const video = videoStorage.findById(targetId);
    if (video) {
      videoStorage.update(targetId, { collectionCount: Math.max(0, (video.collectionCount || 0) + delta) });
    }
  } else if (targetType === 'product') {
    const product = productStorage.findById(targetId);
    if (product) {
      productStorage.update(targetId, { collectionCount: Math.max(0, (product.collectionCount || 0) + delta) });
    }
  }
}

export default router;
