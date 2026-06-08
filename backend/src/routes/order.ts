import { Router, Response } from 'express';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { v4 as uuidv4 } from 'uuid';
import { WebSocketServer } from '../websocket/wsServer';

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
const liveStorage = new StorageService<any>('live_rooms.json');
const lrpStorage = new StorageService<any>('live_room_products.json');
const couponStorage = new StorageService<any>('coupons.json');
const userCouponStorage = new StorageService<any>('user_coupons.json');

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

  // Support new couponId-based discount
  const couponId = req.body.couponId as string | undefined;
  let discountAmount = requestedDiscount;

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

  // Validate and calculate coupon discount from couponId
  if (couponId) {
    const coupon = couponStorage.findById(couponId);
    if (!coupon) {
      res.status(400).json({ error: 'Coupon not found' });
      return;
    }
    const userCoupons = userCouponStorage.query(uc => uc.userId === req.user!.id && uc.couponId === couponId);
    if (userCoupons.length === 0) {
      res.status(400).json({ error: 'You have not claimed this coupon' });
      return;
    }
    const userCoupon = userCoupons[0];
    if (userCoupon.used) {
      res.status(400).json({ error: 'Coupon already used' });
      return;
    }
    if (new Date(coupon.validTo) < new Date()) {
      res.status(400).json({ error: 'Coupon has expired' });
      return;
    }
    if (totalAmount < (coupon.minPurchase || 0)) {
      res.status(400).json({ error: `Minimum purchase ¥${coupon.minPurchase} required` });
      return;
    }
    // Calculate discount
    if (coupon.type === 'percentage') {
      discountAmount = Math.round(totalAmount * coupon.value / 100);
    } else {
      discountAmount = Math.min(coupon.value, totalAmount);
    }
    // Mark coupon as used
    userCouponStorage.update(userCoupon.id, { used: true });
  }

  const now = new Date().toISOString();
  const order: OrderData = {
    id: uuidv4(),
    userId: req.user!.id,
    status: 'pending',
    totalAmount,
    discountAmount,
    finalAmount: Math.max(0, totalAmount - discountAmount),
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

  // Broadcast purchase events to active live rooms
  try {
    const wss = req.app.locals.wss as WebSocketServer | undefined;
    if (wss) {
      const activeRooms = liveStorage.query(r => r.status === 'live');
      for (const item of orderItems) {
        const product = productStorage.findById(item.productId);
        if (!product) continue;
        const bindings = lrpStorage.query(b => b.productId === item.productId);
        for (const binding of bindings) {
          const room = activeRooms.find(r => r.id === binding.roomId);
          if (room) {
            wss.pushPurchase(room.id, {
              username: req.user!.username || '匿名用户',
              productTitle: product.title,
              quantity: item.quantity,
            });
          }
        }
      }
    }
  } catch { /* ignore ws errors */ }

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

// PATCH /api/orders/:id/receive - mark paid order as completed
router.patch('/:id/receive', (req: AuthRequest, res: Response) => {
  const order = orderStorage.findById(req.params.id as string);
  if (!order || order.userId !== req.user!.id) {
    res.status(404).json({ error: 'Order not found' });
    return;
  }
  if (order.status !== 'paid' && order.status !== 'shipped') {
    res.status(400).json({ error: 'Can only confirm receipt for paid/shipped orders' });
    return;
  }
  const updated = orderStorage.update(req.params.id as string, { status: 'completed' as const });
  res.json(updated);
});

// DELETE /api/orders/:id - delete a completed order
router.delete('/:id', (req: AuthRequest, res: Response) => {
  const order = orderStorage.findById(req.params.id as string);
  if (!order || order.userId !== req.user!.id) {
    res.status(404).json({ error: 'Order not found' });
    return;
  }
  if (order.status !== 'completed') {
    res.status(400).json({ error: 'Can only delete completed orders' });
    return;
  }
  const items = orderItemStorage.query(i => i.orderId === order.id);
  items.forEach(item => orderItemStorage.delete(item.id));
  orderStorage.delete(order.id);
  res.json({ success: true });
});

// POST /api/orders/:id/remind - send reminder message to merchant
router.post('/:id/remind', (req: AuthRequest, res: Response) => {
  const order = orderStorage.findById(req.params.id as string);
  if (!order || order.userId !== req.user!.id) {
    res.status(404).json({ error: 'Order not found' });
    return;
  }
  if (order.status !== 'paid') {
    res.status(400).json({ error: 'Can only remind for paid orders' });
    return;
  }

  // Find the merchant - get the order's product owner
  const orderItems = orderItemStorage.query((i: OrderItemData) => i.orderId === order.id);
  let merchantId: string | null = null;
  for (const item of orderItems) {
    const product = productStorage.findById(item.productId);
    if (product && product.ownerId) {
      merchantId = product.ownerId;
      break;
    }
  }

  if (!merchantId) {
    res.status(400).json({ error: 'Could not find merchant for this order' });
    return;
  }

  // Save message via the messages system
  try {
    const messageStorage = new StorageService<any>('messages.json');

    // Conversation ID follows the same convention as message.ts: sorted user IDs joined by _
    const conversationId = [req.user!.id, merchantId].sort().join('_');

    const shortId = order.id.substring(0, 8);
    const messageContent = `订单催单提醒\n订单号：${shortId}\n金额：¥${order.finalAmount.toFixed(2)}\n点击查看订单详情`;

    const msg = {
      id: uuidv4(),
      conversationId,
      senderId: req.user!.id,
      receiverId: merchantId,
      content: messageContent,
      type: 'order_remind',
      orderId: order.id,
      status: 'sent',
      createdAt: new Date().toISOString(),
    };

    messageStorage.create(msg);

    // Push via WebSocket to merchant if online
    const wss = (req.app as any).locals?.wss;
    if (wss?.pushMessage) {
      wss.pushMessage(merchantId, {
        type: 'NEW_MESSAGE',
        message: { ...msg, senderUsername: req.user!.username },
      });
    }
  } catch (e) {
    // Non-fatal
  }

  res.json({ success: true, message: 'Reminder sent' });
});

export default router;
