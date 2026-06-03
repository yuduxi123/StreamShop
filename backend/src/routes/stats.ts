import { Router, Response } from 'express';
import { StorageService } from '../services/storage.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';

interface ProductData {
  id: string; title: string; price: number; coverUrl: string; salesCount: number; category: string;
}
interface OrderData {
  id: string; status: string; totalAmount: number; discount: number; finalAmount: number; createdAt: string;
}
interface VideoData {
  id: string; title: string; coverUrl: string; authorId: string;
  viewCount: number; likeCount: number; commentCount: number; shareCount: number;
}
interface LiveRoomData {
  id: string; title: string; anchorId: string; status: string;
  onlineCount: number; likeCount: number; viewerCount: number;
}
interface CartItemData {
  id: string; userId: string; productId: string; quantity: number;
}
interface OrderItemData {
  id: string; orderId: string; productId: string; quantity: number; unitPrice: number; subtotal: number;
}
interface VideoProductData {
  id: string; videoId: string; productId: string;
}

const productStorage = new StorageService<ProductData>('products.json');
const orderStorage = new StorageService<OrderData>('orders.json');
const videoStorage = new StorageService<VideoData>('videos.json');
const liveStorage = new StorageService<LiveRoomData>('live_rooms.json');
const cartStorage = new StorageService<CartItemData>('carts.json');
const orderItemStorage = new StorageService<OrderItemData>('order_items.json');
const videoProductStorage = new StorageService<VideoProductData>('video_products.json');

const router = Router();

router.get('/dashboard', authMiddleware, (req: AuthRequest, res: Response) => {
  const userId = req.user!.id;

  const allOrders = orderStorage.findAll();
  const allProducts = productStorage.findAll();
  const allVideos = videoStorage.findAll();
  const allRooms = liveStorage.findAll();
  const cartItems = cartStorage.findAll();
  const orderItems = orderItemStorage.findAll();
  const videoProducts = videoProductStorage.findAll();

  // Filter data by current user
  const userVideos = allVideos.filter(v => v.authorId === userId);
  const userRooms = allRooms.filter(r => r.anchorId === userId);
  const userVideoIds = new Set(userVideos.map(v => v.id));

  // Products linked to user's videos
  const userVideoProducts = videoProducts.filter(vp => userVideoIds.has(vp.videoId));
  const linkedProductIds = new Set(userVideoProducts.map(vp => vp.productId));
  const userProducts = allProducts.filter(p => linkedProductIds.has(p.id));

  // Products linked to each user product (for bindCount)
  const productBindCounts: Record<string, number> = {};
  for (const vp of videoProducts) {
    productBindCounts[vp.productId] = (productBindCounts[vp.productId] || 0) + 1;
  }

  // Platform-wide product bind counts (for topProducts & gmvRanking)
  const platformProductBindCounts: Record<string, number> = {};
  for (const vp of videoProducts) {
    platformProductBindCounts[vp.productId] = (platformProductBindCounts[vp.productId] || 0) + 1;
  }

  // KPI — scoped to current user
  const totalVideos = userVideos.length;
  const totalViews = userVideos.reduce((s, v) => s + (v.viewCount || 0), 0);
  const totalLikes = userVideos.reduce((s, v) => s + (v.likeCount || 0), 0);
  const totalComments = userVideos.reduce((s, v) => s + (v.commentCount || 0), 0);
  const totalRooms = userRooms.length;
  const activeRooms = userRooms.filter(r => r.status === 'live').length;

  // Product conversion funnel — based on real data
  const videosWithProducts = new Set(userVideoProducts.map(vp => vp.videoId));
  const exposure = userVideos
    .filter(v => videosWithProducts.has(v.id))
    .reduce((s, v) => s + (v.viewCount || 0), 0);

  // Top products — platform-wide, sorted by how many videos link to each product
  const topProducts = allProducts
    .map(p => ({
      id: p.id,
      title: p.title,
      price: p.price,
      coverUrl: p.coverUrl,
      category: p.category,
      bindCount: platformProductBindCounts[p.id] || 0,
    }))
    .sort((a, b) => b.bindCount - a.bindCount)
    .slice(0, 10);

  // Per-video performance — real data, sorted by views
  const videoPerformance = userVideos
    .sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0))
    .map(v => ({
      id: v.id,
      title: v.title,
      coverUrl: v.coverUrl,
      views: v.viewCount || 0,
      likes: v.likeCount || 0,
      comments: v.commentCount || 0,
      shares: v.shareCount || 0,
    }));

  // GMV by month (last 6 months) — for user's products
  const userProductIds = new Set(userProducts.map(p => p.id));
  const now = new Date();
  const monthlyGMV: { month: string; gmv: number }[] = [];
  for (let i = 5; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    const gmv = allOrders
      .filter(o => o.status !== 'cancelled' && o.createdAt?.startsWith(key))
      .reduce((s, o) => {
        const items = orderItems.filter(oi => oi.orderId === o.id && userProductIds.has(oi.productId));
        return s + items.reduce((sum, oi) => sum + (oi.subtotal || 0), 0);
      }, 0);
    monthlyGMV.push({ month: key, gmv });
  }

  // GMV ranking by video — platform-wide
  const productToVideo: Record<string, string> = {};
  for (const vp of videoProducts) {
    productToVideo[vp.productId] = vp.videoId;
  }
  const videoGMV: Record<string, number> = {};
  for (const oi of orderItems) {
    const order = allOrders.find(o => o.id === oi.orderId);
    if (!order || order.status === 'cancelled') continue;
    const vid = productToVideo[oi.productId];
    if (!vid) continue;
    videoGMV[vid] = (videoGMV[vid] || 0) + (oi.subtotal || 0);
  }
  const gmvRanking = Object.entries(videoGMV)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
    .map(([id, gmv]) => {
      const video = allVideos.find(v => v.id === id);
      return {
        id,
        name: video ? video.title : `视频: ${id}`,
        gmv: Math.round(gmv * 100) / 100,
      };
    });

  res.json({
    kpi: {
      totalVideos,
      totalViews,
      totalLikes,
      totalComments,
      totalRooms,
      activeRooms,
    },
    productFunnel: {
      exposure,
      productVideos: videosWithProducts.size,
      linkedProducts: linkedProductIds.size,
      totalProducts: userProducts.length,
    },
    topProducts,
    videoPerformance,
    monthlyGMV,
    gmvRanking,
  });
});

export default router;
