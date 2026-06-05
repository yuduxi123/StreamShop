import { Router, Request, Response } from 'express';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';

interface CouponData {
  id: string;
  title: string;
  type: 'fixed' | 'percentage';
  value: number;
  minPurchase: number;
  stock: number;
  validFrom: string;
  validTo: string;
  liveRoomId?: string;
  productScope?: 'all' | 'specific';
  productIds?: string[];
  claimDeadline?: string;
  createdAt: string;
}

interface UserCouponData {
  id: string;
  userId: string;
  couponId: string;
  used: boolean;
  claimedAt: string;
}

const couponStorage = new StorageService<CouponData>('coupons.json');
const userCouponStorage = new StorageService<UserCouponData>('user_coupons.json');

const router = Router();

// GET /api/coupons
router.get('/', (_req: Request, res: Response) => {
  const coupons = couponStorage.findAll().filter(c => c.stock > 0 && new Date(c.validTo) >= new Date());
  res.json(coupons);
});

// GET /api/coupons/my - get user's claimed coupons (must be before /:id)
router.get('/my', authMiddleware, (req: AuthRequest, res: Response) => {
  const userCoupons = userCouponStorage.query(uc => uc.userId === req.user!.id);
  const enriched = userCoupons.map(uc => {
    const coupon = couponStorage.findById(uc.couponId);
    return { ...uc, coupon };
  });
  res.json(enriched);
});

// GET /api/coupons/:id
router.get('/:id', (req: Request, res: Response) => {
  const coupon = couponStorage.findById(req.params.id as string);
  if (!coupon) {
    res.status(404).json({ error: 'Coupon not found' });
    return;
  }
  res.json(coupon);
});

// POST /api/coupons/:id/claim
router.post('/:id/claim', authMiddleware, (req: AuthRequest, res: Response) => {
  const coupon = couponStorage.findById(req.params.id as string);
  if (!coupon) {
    res.status(404).json({ error: 'Coupon not found' });
    return;
  }

  // Check expiry
  if (new Date(coupon.validTo) < new Date()) {
    res.status(400).json({ error: 'Coupon has expired' });
    return;
  }

  // Check stock
  if (coupon.stock <= 0) {
    res.status(400).json({ error: 'Coupon out of stock' });
    return;
  }

  // Check if already claimed
  const existing = userCouponStorage.query(uc => uc.userId === req.user!.id && uc.couponId === coupon.id);
  if (existing.length > 0) {
    res.status(400).json({ error: 'Coupon already claimed' });
    return;
  }

  // Decrement stock
  couponStorage.update(coupon.id, { stock: coupon.stock - 1 });

  // Create user coupon
  const userCoupon: UserCouponData = {
    id: req.user!.id + '_' + coupon.id,
    userId: req.user!.id,
    couponId: coupon.id,
    used: false,
    claimedAt: new Date().toISOString(),
  };
  userCouponStorage.create(userCoupon);
  res.status(201).json(userCoupon);
});

export default router;
