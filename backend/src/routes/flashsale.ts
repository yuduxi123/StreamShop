import { Router, Request, Response } from 'express';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { v4 as uuidv4 } from 'uuid';

interface FlashSaleData {
  id: string;
  productId: string;
  flashPrice: number;
  stock: number;
  maxPerUser: number;
  startTime: string;
  endTime: string;
  status: 'upcoming' | 'active' | 'ended';
  createdAt: string;
}

interface FlashSaleOrderData {
  id: string;
  flashSaleId: string;
  userId: string;
  productId: string;
  quantity: number;
  price: number;
  createdAt: string;
}

const flashSaleStorage = new StorageService<FlashSaleData>('flash_sales.json');
const flashOrderStorage = new StorageService<FlashSaleOrderData>('flash_orders.json');
const productStorage = new StorageService<any>('products.json');

const router = Router();

// GET /api/flash-sales - active flash sales
router.get('/', (_req: Request, res: Response) => {
  const now = new Date().toISOString();
  const all = flashSaleStorage.findAll().filter(fs => fs.status !== 'ended');
  // Auto-update status
  const updated = all.map(fs => {
    if (fs.status === 'upcoming' && fs.startTime <= now) {
      flashSaleStorage.update(fs.id, { status: 'active' });
      return { ...fs, status: 'active' as const };
    }
    if (fs.status === 'active' && fs.endTime <= now) {
      flashSaleStorage.update(fs.id, { status: 'ended' });
      return { ...fs, status: 'ended' as const };
    }
    return fs;
  });
  const enriched = updated
    .filter(fs => fs.status === 'active' || fs.status === 'upcoming')
    .map(fs => {
      const product = productStorage.findById(fs.productId);
      return { ...fs, product };
    });
  res.json(enriched);
});

// POST /api/flash-sales - admin creates flash sale
router.post('/', authMiddleware, (req: AuthRequest, res: Response) => {
  const { productId, flashPrice, stock, maxPerUser = 1, startTime, endTime } = req.body;
  if (!productId || !flashPrice || !startTime || !endTime) {
    res.status(400).json({ error: 'Missing required fields' });
    return;
  }
  const product = productStorage.findById(productId);
  if (!product) {
    res.status(404).json({ error: 'Product not found' });
    return;
  }
  const now = new Date().toISOString();
  const fs: FlashSaleData = {
    id: uuidv4(),
    productId,
    flashPrice,
    stock: stock || product.stock,
    maxPerUser,
    startTime,
    endTime,
    status: startTime <= now ? 'active' : 'upcoming',
    createdAt: now,
  };
  flashSaleStorage.create(fs);
  res.status(201).json({ ...fs, product });
});

// POST /api/flash-sales/:id/buy
router.post('/:id/buy', authMiddleware, (req: AuthRequest, res: Response) => {
  const fs = flashSaleStorage.findById(req.params.id as string);
  if (!fs) {
    res.status(404).json({ error: 'Flash sale not found' });
    return;
  }
  if (fs.status !== 'active' || fs.endTime <= new Date().toISOString()) {
    res.status(400).json({ error: 'Flash sale is not active' });
    return;
  }
  if (fs.stock <= 0) {
    res.status(400).json({ error: 'Sold out' });
    return;
  }

  const quantity = req.body.quantity || 1;
  // Check user limit
  const userOrders = flashOrderStorage.query(fo => fo.flashSaleId === fs.id && fo.userId === req.user!.id);
  const userTotal = userOrders.reduce((s, o) => s + o.quantity, 0);
  if (userTotal + quantity > fs.maxPerUser) {
    res.status(400).json({ error: 'Exceeded per-user limit' });
    return;
  }

  flashSaleStorage.update(fs.id, { stock: fs.stock - quantity });

  const order: FlashSaleOrderData = {
    id: uuidv4(),
    flashSaleId: fs.id,
    userId: req.user!.id,
    productId: fs.productId,
    quantity,
    price: fs.flashPrice,
    createdAt: new Date().toISOString(),
  };
  flashOrderStorage.create(order);
  res.status(201).json(order);
});

export default router;
