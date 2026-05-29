import { Router, Response } from 'express';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { v4 as uuidv4 } from 'uuid';

interface CartData {
  id: string;
  userId: string;
  productId: string;
  quantity: number;
  selected: boolean;
  createdAt: string;
}

const cartStorage = new StorageService<CartData>('carts.json');
const productStorage = new StorageService<any>('products.json');

const router = Router();

// All cart routes require auth
router.use(authMiddleware);

// GET /api/cart
router.get('/', (req: AuthRequest, res: Response) => {
  const items = cartStorage.query(c => c.userId === req.user!.id);
  const enriched = items.map(item => {
    const product = productStorage.findById(item.productId);
    return { ...item, product };
  });
  res.json(enriched);
});

// POST /api/cart - add or update quantity
router.post('/', (req: AuthRequest, res: Response) => {
  const { productId, quantity } = req.body;
  if (!productId) {
    res.status(400).json({ error: 'productId required' });
    return;
  }

  const product = productStorage.findById(productId);
  if (!product) {
    res.status(404).json({ error: 'Product not found' });
    return;
  }
  const qtyToAdd = Number(quantity ?? 1);
  if (!Number.isFinite(qtyToAdd) || qtyToAdd <= 0) {
    res.status(400).json({ error: 'quantity must be a positive number' });
    return;
  }

  const existing = cartStorage.query(c => c.userId === req.user!.id && c.productId === productId);
  if (existing.length > 0) {
    const item = existing[0];
    const nextQty = item.quantity + qtyToAdd;
    if (nextQty > (product.stock ?? 0)) {
      res.status(400).json({ error: 'Insufficient stock' });
      return;
    }
    const updated = cartStorage.update(item.id, { quantity: nextQty });
    res.json(updated);
    return;
  }
  if (qtyToAdd > (product.stock ?? 0)) {
    res.status(400).json({ error: 'Insufficient stock' });
    return;
  }

  const item: CartData = {
    id: uuidv4(),
    userId: req.user!.id,
    productId,
    quantity: qtyToAdd,
    selected: true,
    createdAt: new Date().toISOString(),
  };
  cartStorage.create(item);
  res.status(201).json(item);
});

// PATCH /api/cart/:id
router.patch('/:id', (req: AuthRequest, res: Response) => {
  const item = cartStorage.findById(req.params.id as string);
  if (!item || item.userId !== req.user!.id) {
    res.status(404).json({ error: 'Cart item not found' });
    return;
  }
  if (req.body.quantity !== undefined) {
    const qty = Number(req.body.quantity);
    if (!Number.isFinite(qty) || qty <= 0) {
      res.status(400).json({ error: 'quantity must be a positive number' });
      return;
    }
    const product = productStorage.findById(item.productId);
    if (!product) {
      res.status(404).json({ error: 'Product not found' });
      return;
    }
    if (qty > (product.stock ?? 0)) {
      res.status(400).json({ error: 'Insufficient stock' });
      return;
    }
  }
  const updated = cartStorage.update(req.params.id as string, req.body);
  res.json(updated);
});

// DELETE /api/cart/:id
router.delete('/:id', (req: AuthRequest, res: Response) => {
  const item = cartStorage.findById(req.params.id as string);
  if (!item || item.userId !== req.user!.id) {
    res.status(404).json({ error: 'Cart item not found' });
    return;
  }
  cartStorage.delete(req.params.id as string);
  res.status(204).send();
});

// DELETE /api/cart - clear selected items (after order placed)
router.delete('/', (req: AuthRequest, res: Response) => {
  const items = cartStorage.query(c => c.userId === req.user!.id && c.selected);
  items.forEach(item => cartStorage.delete(item.id));
  res.status(204).send();
});

export default router;
