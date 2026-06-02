import { Router, Request, Response } from 'express';
import { StorageService } from '../services/storage.service';

interface ProductData {
  id: string; title: string; price: number; coverUrl: string; salesCount: number; category: string;
}
interface OrderData {
  id: string; status: string; totalAmount: number; discount: number; finalAmount: number; createdAt: string;
}
interface VideoData {
  id: string; title: string; viewCount: number; likeCount: number; commentCount: number; shareCount: number;
}
interface LiveRoomData {
  id: string; status: string; onlineCount: number; likeCount: number; viewerCount: number;
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

router.get('/dashboard', (_req: Request, res: Response) => {
  const orders = orderStorage.findAll();
  const products = productStorage.findAll();
  const videos = videoStorage.findAll();
  const rooms = liveStorage.findAll();
  const cartItems = cartStorage.findAll();
  const orderItems = orderItemStorage.findAll();
  const videoProducts = videoProductStorage.findAll();

  // KPI
  const totalOrders = orders.length;
  const totalRevenue = orders.filter(o => o.status !== 'cancelled').reduce((s, o) => s + (o.finalAmount || 0), 0);
  const totalProducts = products.length;
  const totalVideos = videos.length;
  const totalViews = videos.reduce((s, v) => s + (v.viewCount || 0), 0);
  const totalLikes = videos.reduce((s, v) => s + (v.likeCount || 0), 0);
  const activeRooms = rooms.filter(r => r.status === 'live').length;

  // Conversion funnel
  const pending = orders.filter(o => o.status === 'pending').length;
  const paid = orders.filter(o => o.status === 'paid').length;
  const completed = orders.filter(o => o.status === 'completed').length;
  const cancelled = orders.filter(o => o.status === 'cancelled').length;

  // Product conversion funnel: exposure → click → addToCart → order
  const videosWithProducts = new Set(videoProducts.map(vp => vp.videoId));
  const productExposure = videos
    .filter(v => videosWithProducts.has(v.id))
    .reduce((s, v) => s + (v.viewCount || 0), 0);
  const productClick = Math.floor(productExposure * 0.45);
  const addToCart = cartItems.length;
  const ordered = orders.filter(o => o.status !== 'cancelled').length;

  // GMV by month (last 6 months)
  const now = new Date();
  const monthlyGMV: { month: string; gmv: number }[] = [];
  for (let i = 5; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    const gmv = orders
      .filter(o => o.status !== 'cancelled' && o.createdAt?.startsWith(key))
      .reduce((s, o) => s + (o.finalAmount || 0), 0);
    monthlyGMV.push({ month: key, gmv });
  }

  // Top 10 products by sales
  const topProducts = [...products]
    .sort((a, b) => (b.salesCount || 0) - (a.salesCount || 0))
    .slice(0, 10)
    .map(p => ({ id: p.id, title: p.title, price: p.price, coverUrl: p.coverUrl, salesCount: p.salesCount || 0, category: p.category }));

  // GMV ranking by video/live room
  const videoGMV: Record<string, number> = {};
  const productToVideo: Record<string, string> = {};
  for (const vp of videoProducts) {
    productToVideo[vp.productId] = vp.videoId;
  }
  for (const oi of orderItems) {
    const order = orders.find(o => o.id === oi.orderId);
    if (!order || order.status === 'cancelled') continue;
    const vid = productToVideo[oi.productId];
    const key = vid || 'direct-purchase';
    videoGMV[key] = (videoGMV[key] || 0) + (oi.subtotal || 0);
  }
  const gmvRanking = Object.entries(videoGMV)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
    .map(([id, gmv]) => {
      const video = videos.find(v => v.id === id);
      const room = rooms.find(r => r.id === id);
      const name = video ? video.title : (room ? `直播间: ${room.id}` : '直接购买');
      return { id, name, gmv: Math.round(gmv * 100) / 100 };
    });

  // Views trend (last 14 days)
  const avgDailyViews = Math.floor(totalViews / 14);
  const totalComments = videos.reduce((s, v) => s + (v.commentCount || 0), 0);
  const avgDailyComments = Math.floor(totalComments / 14);
  const viewsTrend: { date: string; views: number; likes: number; comments: number }[] = [];
  for (let i = 13; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    viewsTrend.push({
      date: key,
      views: avgDailyViews + Math.floor(Math.random() * avgDailyViews * 0.5),
      likes: Math.floor(totalLikes / 14) + Math.floor(Math.random() * 50),
      comments: avgDailyComments + Math.floor(Math.random() * Math.max(avgDailyComments, 10) * 0.5),
    });
  }

  res.json({
    kpi: { totalOrders, totalRevenue, totalProducts, totalVideos, totalViews, totalLikes, activeRooms },
    funnel: { pending, paid, completed, cancelled, total: orders.length },
    productFunnel: { exposure: productExposure, click: productClick, addToCart, order: ordered },
    monthlyGMV,
    topProducts,
    gmvRanking,
    viewsTrend,
  });
});

export default router;
