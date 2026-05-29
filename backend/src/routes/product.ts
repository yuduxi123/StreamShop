import { Router, Request, Response } from 'express';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { v4 as uuidv4 } from 'uuid';

interface ProductData {
  id: string;
  ownerId?: string;
  title: string;
  description: string;
  price: number;
  originalPrice: number;
  coverUrl: string;
  stock: number;
  salesCount: number;
  status: string;
  category: string;
  tags: string[];
  createdAt: string;
}

interface VideoProduct {
  id: string;
  videoId: string;
  productId: string;
  displayOrder: number;
}

const productStorage = new StorageService<ProductData>('products.json');
const vpStorage = new StorageService<VideoProduct>('video_products.json');

const router = Router();

// GET /api/products
router.get('/', (req: Request, res: Response) => {
  const page = parseInt(req.query.page as string) || 1;
  const limit = parseInt(req.query.limit as string) || 20;
  const category = req.query.category as string;
  const search = req.query.search as string;

  let all = productStorage.findAll();

  if (category) {
    all = all.filter(p => p.category === category);
  }
  if (search) {
    const q = search.toLowerCase();
    all = all.filter(p => p.title.toLowerCase().includes(q) || p.description.toLowerCase().includes(q));
  }

  const total = all.length;
  const start = (page - 1) * limit;
  const data = all.slice(start, start + limit);
  res.json({ data, total, page, limit });
});

// GET /api/products/:id
router.get('/:id', (req: Request, res: Response) => {
  const product = productStorage.findById(req.params.id as string);
  if (!product) {
    res.status(404).json({ error: 'Product not found' });
    return;
  }
  // Get associated videos
  const bindings = vpStorage.query(vp => vp.productId === product.id);
  res.json({ ...product, videoBindings: bindings });
});

// POST /api/products
router.post('/', authMiddleware, (req: AuthRequest, res: Response) => {
  const { title, description, price, stock, category } = req.body;
  const coverUrl = req.body.coverUrl ?? req.body.cover_url;
  const originalPrice = req.body.originalPrice ?? req.body.original_price;
  if (!title || price === undefined) {
    res.status(400).json({ error: 'Title and price required' });
    return;
  }
  const product: ProductData = {
    id: uuidv4(),
    ownerId: req.user!.id,
    title,
    description: description || '',
    price,
    originalPrice: originalPrice || price,
    coverUrl: coverUrl || '',
    stock: stock ?? 9999,
    salesCount: 0,
    status: 'active',
    category: category || '',
    tags: req.body.tags || [],
    createdAt: new Date().toISOString(),
  };
  productStorage.create(product);
  res.status(201).json(product);
});

// PATCH /api/products/:id
router.patch('/:id', authMiddleware, (req: AuthRequest, res: Response) => {
  const id = req.params.id as string;
  const existing = productStorage.findById(id);
  if (!existing) {
    res.status(404).json({ error: 'Product not found' });
    return;
  }
  if (existing.ownerId && existing.ownerId !== req.user!.id && req.user!.role !== 'admin') {
    res.status(403).json({ error: 'Forbidden' });
    return;
  }

  const updates: any = { ...req.body };
  if (updates.cover_url !== undefined) {
    updates.coverUrl = updates.cover_url;
    delete updates.cover_url;
  }
  if (updates.original_price !== undefined) {
    updates.originalPrice = updates.original_price;
    delete updates.original_price;
  }
  if (!existing.ownerId) {
    updates.ownerId = req.user!.id;
  }

  const updated = productStorage.update(id, updates);
  if (!updated) {
    res.status(404).json({ error: 'Product not found' });
    return;
  }
  res.json(updated);
});

// DELETE /api/products/:id
router.delete('/:id', authMiddleware, (req: AuthRequest, res: Response) => {
  const id = req.params.id as string;
  const existing = productStorage.findById(id);
  if (!existing) {
    res.status(404).json({ error: 'Product not found' });
    return;
  }
  if (existing.ownerId && existing.ownerId !== req.user!.id && req.user!.role !== 'admin') {
    res.status(403).json({ error: 'Forbidden' });
    return;
  }

  const deleted = productStorage.delete(id);
  if (!deleted) {
    res.status(404).json({ error: 'Product not found' });
    return;
  }
  res.status(204).send();
});

// POST /api/products/:id/bind-video
router.post('/:id/bind-video', authMiddleware, (req: AuthRequest, res: Response) => {
  const { videoId, displayOrder } = req.body;
  if (!videoId) {
    res.status(400).json({ error: 'videoId required' });
    return;
  }
  const product = productStorage.findById(req.params.id as string);
  if (!product) {
    res.status(404).json({ error: 'Product not found' });
    return;
  }
  if (product.ownerId && product.ownerId !== req.user!.id && req.user!.role !== 'admin') {
    res.status(403).json({ error: 'Forbidden' });
    return;
  }
  const binding: VideoProduct = {
    id: videoId + '_' + (req.params.id as string),
    videoId,
    productId: req.params.id as string,
    displayOrder: displayOrder || 0,
  };
  vpStorage.create(binding);
  res.status(201).json(binding);
});

export default router;
