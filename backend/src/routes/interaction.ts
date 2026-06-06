import { Router, Response } from 'express';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { getRequestMediaContext, shouldExposeVideo } from '../services/media-url.service';

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

interface FollowData {
  id: string;
  followerId: string;
  followingId: string;
  createdAt: string;
}

const likeStorage = new StorageService<LikeData>('likes.json');
const collectionStorage = new StorageService<CollectionData>('collections.json');
const followStorage = new StorageService<FollowData>('follows.json');
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
      if (video && shouldExposeVideo(video, getRequestMediaContext(req))) {
        const users = userStorage.query(u => u.id === video.authorId);
        const author = users.length > 0 ? { id: users[0].id, username: users[0].username, avatarUrl: users[0].avatarUrl } : null;
        const bindings = vpStorage.query((vp: any) => vp.videoId === video.id);
        const products = bindings.map((b: any) => productStorage.findById(b.productId)).filter(Boolean);
        target = { ...video, author, products };
      }
    } else if (item.targetType === 'product') {
      target = productStorage.findById(item.targetId);
    }
    return target ? { ...item, target } : null;
  }).filter(Boolean);
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
      if (video && shouldExposeVideo(video, getRequestMediaContext(req))) {
        const users = userStorage.query(u => u.id === video.authorId);
        const author = users.length > 0 ? { id: users[0].id, username: users[0].username, avatarUrl: users[0].avatarUrl } : null;
        const bindings = vpStorage.query((vp: any) => vp.videoId === video.id);
        const products = bindings.map((b: any) => productStorage.findById(b.productId)).filter(Boolean);
        target = { ...video, author, products };
      }
    } else if (item.targetType === 'product') {
      target = productStorage.findById(item.targetId);
    }
    return target ? { ...item, target } : null;
  }).filter(Boolean);
  res.json(enriched);
});

// ---- FOLLOWS ----

// POST /api/interactions/follow/:id - toggle follow a user
router.post('/follow/:id', authMiddleware, (req: AuthRequest, res: Response) => {
  const followingId = req.params.id as string;
  const followerId = req.user!.id;

  if (followerId === followingId) {
    res.status(400).json({ error: 'Cannot follow yourself' });
    return;
  }

  // Check target user exists
  const targetUser = userStorage.findById(followingId);
  if (!targetUser) {
    res.status(404).json({ error: 'User not found' });
    return;
  }

  const existing = followStorage.query(
    f => f.followerId === followerId && f.followingId === followingId
  );

  if (existing.length > 0) {
    // Unfollow
    followStorage.delete(existing[0].id);
    updateFollowCount(followerId, followingId, -1);
    res.json({ following: false });
  } else {
    // Follow
    const follow: FollowData = {
      id: followerId + '_' + followingId,
      followerId,
      followingId,
      createdAt: new Date().toISOString(),
    };
    followStorage.create(follow);
    updateFollowCount(followerId, followingId, 1);
    res.json({ following: true });
  }
});

// GET /api/interactions/follow/:id/status - check if following
router.get('/follow/:id/status', authMiddleware, (req: AuthRequest, res: Response) => {
  const followingId = req.params.id as string;
  const followerId = req.user!.id;
  const existing = followStorage.query(
    f => f.followerId === followerId && f.followingId === followingId
  );
  res.json({ following: existing.length > 0 });
});

// GET /api/interactions/followers - list my followers
router.get('/followers', authMiddleware, (req: AuthRequest, res: Response) => {
  const items = followStorage.query(f => f.followingId === req.user!.id);
  const enriched = items.map(item => {
    const user = userStorage.findById(item.followerId);
    return {
      ...item,
      user: user ? { id: user.id, username: user.username, avatarUrl: user.avatarUrl } : null,
    };
  });
  res.json(enriched);
});

// GET /api/interactions/following - list who I follow
router.get('/following', authMiddleware, (req: AuthRequest, res: Response) => {
  const items = followStorage.query(f => f.followerId === req.user!.id);
  const enriched = items.map(item => {
    const user = userStorage.findById(item.followingId);
    return {
      ...item,
      user: user ? { id: user.id, username: user.username, avatarUrl: user.avatarUrl } : null,
    };
  });
  res.json(enriched);
});

function updateFollowCount(followerId: string, followingId: string, delta: number): void {
  const follower = userStorage.findById(followerId);
  if (follower) {
    userStorage.update(followerId, { following: Math.max(0, (follower.following || 0) + delta) });
  }
  const following = userStorage.findById(followingId);
  if (following) {
    userStorage.update(followingId, { followers: Math.max(0, (following.followers || 0) + delta) });
  }
}

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
