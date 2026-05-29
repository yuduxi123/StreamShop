import { Router, Response } from 'express';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { v4 as uuidv4 } from 'uuid';

interface OrderData {
  id: string;
  userId: string;
  status: 'pending' | 'paid' | 'shipped' | 'completed' | 'cancelled';
  totalAmount: number;
  discountAmount: number;
  finalAmount: number;
  shippingAddress: string;
  createdAt: string;
}

interface OrderItemData {
  id: string;
  orderId: string;
  productId: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

const orderStorage = new StorageService<OrderData>('orders.json');
const orderItemStorage = new StorageService<OrderItemData>('order_items.json');
const cartStorage = new StorageService<any>('carts.json');
const productStorage = new StorageService<any>('products.json');

const router = Router();
router.use(authMiddleware);

// POST /api/orders - create order from selected cart items, or from provided items (buy now)
router.post('/', (req: AuthRequest, res: Response) => {
  const { shippingAddress, items: directItems } = req.body;
  if (!shippingAddress) {
    res.status(400).json({ error: 'shippingAddress required' });
    return;
  }

  const requestedDiscount = Number(req.body.couponDiscount || 0);
  if (!Number.isFinite(requestedDiscount) || requestedDiscount < 0) {
    res.status(400).json({ error: 'Invalid couponDiscount' });
    return;
  }

  let totalAmount = 0;
  const orderItems: OrderItemData[] = [];

  if (directItems && Array.isArray(directItems) && directItems.length > 0) {
    // Buy-now flow: use provided items directly, bypass cart
    for (const di of directItems) {
      const product = productStorage.findById(di.productId);
      if (!product) {
        res.status(404).json({ error: `Product ${di.productId} not found` });
        return;
      }
      const qty = Number(di.quantity ?? 1);
      if (!Number.isFinite(qty) || qty <= 0) {
        res.status(400).json({ error: 'Invalid quantity' });
        return;
      }
      if (qty > (product.stock ?? 0)) {
        res.status(400).json({ error: `Insufficient stock for product ${product.id}` });
        return;
      }

      const unitPrice = product.price;
      const subtotal = unitPrice * qty;
      totalAmount += subtotal;

      orderItems.push({
        id: uuidv4(),
        orderId: '',
        productId: di.productId,
        quantity: qty,
        unitPrice,
        subtotal,
      });
    }
  } else {
    // Cart flow: read from selected cart items
    const cartItems = cartStorage.query(c => c.userId === req.user!.id && c.selected);
    if (cartItems.length === 0) {
      res.status(400).json({ error: 'No items selected in cart' });
      return;
    }

    for (const cartItem of cartItems) {
      const product = productStorage.findById(cartItem.productId);
      if (!product) continue;
      if (!Number.isFinite(cartItem.quantity) || cartItem.quantity <= 0) {
        res.status(400).json({ error: 'Invalid cart quantity' });
        return;
      }
      if (cartItem.quantity > (product.stock ?? 0)) {
        res.status(400).json({ error: `Insufficient stock for product ${product.id}` });
        return;
      }

      const unitPrice = product.price;
      const subtotal = unitPrice * cartItem.quantity;
      totalAmount += subtotal;

      orderItems.push({
        id: uuidv4(),
        orderId: '',
        productId: cartItem.productId,
        quantity: cartItem.quantity,
        unitPrice,
        subtotal,
      });
    }

    // Remove selected cart items after order created
    cartItems.forEach(item => cartStorage.delete(item.id));
  }

  const now = new Date().toISOString();
  const order: OrderData = {
    id: uuidv4(),
    userId: req.user!.id,
    status: 'pending',
    totalAmount,
    discountAmount: requestedDiscount,
    finalAmount: Math.max(0, totalAmount - requestedDiscount),
    shippingAddress,
    createdAt: now,
  };

  orderStorage.create(order);

  // Create order items with order id
  orderItems.forEach(item => {
    item.orderId = order.id;
    orderItemStorage.create(item);
  });

  // Update product sales and stock
  orderItems.forEach(item => {
    const product = productStorage.findById(item.productId);
    if (product) {
      productStorage.update(item.productId, {
        salesCount: product.salesCount + item.quantity,
        stock: Math.max(0, product.stock - item.quantity),
      });
    }
  });

  res.status(201).json({ ...order, items: orderItems });
});

// GET /api/orders?status=&page=
router.get('/', (req: AuthRequest, res: Response) => {
  const page = parseInt(req.query.page as string) || 1;
  const limit = parseInt(req.query.limit as string) || 20;
  const status = req.query.status as string;

  let orders = orderStorage.query(o => o.userId === req.user!.id);
  if (status) {
    orders = orders.filter(o => o.status === status);
  }

  // Sort by newest first
  orders.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

  const total = orders.length;
  const start = (page - 1) * limit;
  const paged = orders.slice(start, start + limit);

  // Enrich with items
  const enriched = paged.map(order => {
    const items = orderItemStorage.query(i => i.orderId === order.id);
    const itemWithProducts = items.map(item => {
      const product = productStorage.findById(item.productId);
      return { ...item, product };
    });
    return { ...order, items: itemWithProducts };
  });

  res.json({ data: enriched, total, page, limit });
});

// GET /api/orders/:id
router.get('/:id', (req: AuthRequest, res: Response) => {
  const order = orderStorage.findById(req.params.id as string);
  if (!order || order.userId !== req.user!.id) {
    res.status(404).json({ error: 'Order not found' });
    return;
  }
  const items = orderItemStorage.query(i => i.orderId === order.id);
  const itemWithProducts = items.map(item => {
    const product = productStorage.findById(item.productId);
    return { ...item, product };
  });
  res.json({ ...order, items: itemWithProducts });
});

// POST /api/orders/:id/pay - mock payment
router.post('/:id/pay', (req: AuthRequest, res: Response) => {
  const order = orderStorage.findById(req.params.id as string);
  if (!order || order.userId !== req.user!.id) {
    res.status(404).json({ error: 'Order not found' });
    return;
  }
  if (order.status !== 'pending') {
    res.status(400).json({ error: 'Order is not pending' });
    return;
  }
  const success = Math.random() > 0.2; // 80% success rate
  const updated = orderStorage.update(req.params.id as string, {
    status: success ? 'paid' as const : 'pending' as const,
  });
  res.json({ success, order: updated });
});

// PATCH /api/orders/:id/cancel
router.patch('/:id/cancel', (req: AuthRequest, res: Response) => {
  const order = orderStorage.findById(req.params.id as string);
  if (!order || order.userId !== req.user!.id) {
    res.status(404).json({ error: 'Order not found' });
    return;
  }
  if (order.status !== 'pending') {
    res.status(400).json({ error: 'Can only cancel pending orders' });
    return;
  }
  const updated = orderStorage.update(req.params.id as string, { status: 'cancelled' as const });
  res.json(updated);
});

export default router;
