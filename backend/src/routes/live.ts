import { Router, Request, Response } from 'express';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { v4 as uuidv4 } from 'uuid';
import { WebSocketServer } from '../websocket/wsServer';

interface LiveRoomData {
  id: string;
  title: string;
  coverUrl: string;
  anchorId: string;
  status: string;
  currentProductId: string | null;
  onlineCount: number;
  likeCount: number;
  viewerCount: number;
  createdAt: string;
}

interface LiveRoomProduct {
  id: string;
  liveRoomId: string;
  productId: string;
  displayOrder: number;
  isExplaining: boolean;
}

const liveStorage = new StorageService<LiveRoomData>('live_rooms.json');
const lrpStorage = new StorageService<LiveRoomProduct>('live_room_products.json');
const productStorage = new StorageService<any>('products.json');
const userStorage = new StorageService<any>('users.json');

const router = Router();

function getWss(req: Request): WebSocketServer {
  return req.app.locals.wss as WebSocketServer;
}

// GET /api/live/rooms
router.get('/rooms', (req: Request, res: Response) => {
  const page = parseInt(req.query.page as string) || 1;
  const limit = parseInt(req.query.limit as string) || 20;
  const result = liveStorage.paginate(page, limit);

  const enriched = result.data.map(room => {
    const users = userStorage.query(u => u.id === room.anchorId);
    const anchor = users.length > 0 ? { id: users[0].id, username: users[0].username, avatarUrl: users[0].avatarUrl } : null;
    const bindings = lrpStorage.query(b => b.liveRoomId === room.id);
    const products = bindings.map(b => productStorage.findById(b.productId)).filter(Boolean);
    return { ...room, anchor, products, productBindings: bindings };
  });

  res.json({ data: enriched, total: result.total, page: result.page, limit: result.limit });
});

// GET /api/live/rooms/:id
router.get('/rooms/:id', (req: Request, res: Response) => {
  const room = liveStorage.findById(req.params.id as string);
  if (!room) {
    res.status(404).json({ error: 'Live room not found' });
    return;
  }
  const users = userStorage.query(u => u.id === room.anchorId);
  const anchor = users.length > 0 ? { id: users[0].id, username: users[0].username, avatarUrl: users[0].avatarUrl } : null;
  const bindings = lrpStorage.query(b => b.liveRoomId === room.id);
  const products = bindings.map(b => productStorage.findById(b.productId)).filter(Boolean);
  res.json({ ...room, anchor, products, productBindings: bindings });
});

// POST /api/live/rooms
router.post('/rooms', authMiddleware, (req: AuthRequest, res: Response) => {
  const { title, coverUrl } = req.body;
  if (!title) {
    res.status(400).json({ error: 'Title required' });
    return;
  }
  const room: LiveRoomData = {
    id: uuidv4(),
    title,
    coverUrl: coverUrl || '',
    anchorId: req.user!.id,
    status: 'offline',
    currentProductId: null,
    onlineCount: 0,
    likeCount: 0,
    viewerCount: 0,
    createdAt: new Date().toISOString(),
  };
  liveStorage.create(room);
  res.status(201).json(room);
});

// PATCH /api/live/rooms/:id
router.patch('/rooms/:id', authMiddleware, (req: AuthRequest, res: Response) => {
  const id = req.params.id as string;
  const room = liveStorage.findById(id);
  if (!room) {
    res.status(404).json({ error: 'Live room not found' });
    return;
  }
  if (room.anchorId !== req.user!.id && req.user!.role !== 'admin') {
    res.status(403).json({ error: 'Forbidden' });
    return;
  }
  const updated = liveStorage.update(id, req.body);
  if (!updated) {
    res.status(404).json({ error: 'Live room not found' });
    return;
  }
  res.json(updated);
});

// POST /api/live/rooms/:id/start
router.post('/rooms/:id/start', authMiddleware, (req: AuthRequest, res: Response) => {
  const id = req.params.id as string;
  const room = liveStorage.findById(id);
  if (!room) {
    res.status(404).json({ error: 'Live room not found' });
    return;
  }
  if (room.anchorId !== req.user!.id && req.user!.role !== 'admin') {
    res.status(403).json({ error: 'Forbidden' });
    return;
  }
  const updated = liveStorage.update(id, { status: 'live', onlineCount: 0, likeCount: 0 });
  if (!updated) {
    res.status(404).json({ error: 'Live room not found' });
    return;
  }
  getWss(req).pushLiveStarted(id);
  res.json(updated);
});

// POST /api/live/rooms/:id/end
router.post('/rooms/:id/end', authMiddleware, (req: AuthRequest, res: Response) => {
  const id = req.params.id as string;
  const room = liveStorage.findById(id);
  if (!room) {
    res.status(404).json({ error: 'Live room not found' });
    return;
  }
  if (room.anchorId !== req.user!.id && req.user!.role !== 'admin') {
    res.status(403).json({ error: 'Forbidden' });
    return;
  }
  const updated = liveStorage.update(id, { status: 'ended' });
  if (!updated) {
    res.status(404).json({ error: 'Live room not found' });
    return;
  }
  res.json(updated);
});

// POST /api/live/rooms/:id/product/:productId/explain
router.post('/rooms/:id/product/:productId/explain', authMiddleware, (req: AuthRequest, res: Response) => {
  const id = req.params.id as string;
  const productId = req.params.productId as string;
  const room = liveStorage.findById(id);
  if (!room) {
    res.status(404).json({ error: 'Live room not found' });
    return;
  }
  if (room.anchorId !== req.user!.id && req.user!.role !== 'admin') {
    res.status(403).json({ error: 'Forbidden' });
    return;
  }

  // Set current explaining product
  liveStorage.update(id, { currentProductId: productId });

  // Update isExplaining flags
  const bindings = lrpStorage.query(b => b.liveRoomId === id);
  bindings.forEach(b => {
    lrpStorage.update(b.liveRoomId + '_' + b.productId, { isExplaining: b.productId === productId });
  });

  getWss(req).pushProductChanged(id, productId, 'explaining');
  res.json({ success: true, currentProductId: productId });
});

// POST /api/live/rooms/:id/products
router.post('/rooms/:id/products', authMiddleware, (req: AuthRequest, res: Response) => {
  const { productId, displayOrder } = req.body;
  const roomId = req.params.id as string;
  if (!productId) {
    res.status(400).json({ error: 'productId required' });
    return;
  }
  const room = liveStorage.findById(roomId);
  if (!room) {
    res.status(404).json({ error: 'Live room not found' });
    return;
  }
  if (room.anchorId !== req.user!.id && req.user!.role !== 'admin') {
    res.status(403).json({ error: 'Forbidden' });
    return;
  }
  const product = productStorage.findById(productId);
  if (!product) {
    res.status(404).json({ error: 'Product not found' });
    return;
  }
  const bindingId = roomId + '_' + productId;
  if (lrpStorage.findById(bindingId)) {
    res.status(409).json({ error: 'Product already bound to this live room' });
    return;
  }
  const binding: LiveRoomProduct = {
    id: bindingId,
    liveRoomId: roomId,
    productId,
    displayOrder: displayOrder || 0,
    isExplaining: false,
  };
  lrpStorage.create(binding);
  getWss(req).pushProductChanged(roomId, productId, 'added');
  res.status(201).json(binding);
});

export default router;
