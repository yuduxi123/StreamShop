import { Router, Request, Response } from 'express';
import { StorageService } from '../services/storage.service';

interface ProductData {
  id: string; title: string; price: number; coverUrl: string; salesCount: number; category: string;
}
interface OrderData {
  id: string; status: string; totalAmount: number; discount: number; finalAmount: number; createdAt: string;
}
interface VideoData {
  id: string; title: string; viewCount: number; likeCount: number; shareCount: number;
}
interface LiveRoomData {
  id: string; status: string; onlineCount: number; likeCount: number; viewerCount: number;
}

const productStorage = new StorageService<ProductData>('products.json');
const orderStorage = new StorageService<OrderData>('orders.json');
const videoStorage = new StorageService<VideoData>('videos.json');
const liveStorage = new StorageService<LiveRoomData>('live_rooms.json');

const router = Router();

router.get('/dashboard', (_req: Request, res: Response) => {
  const orders = orderStorage.findAll();
  const products = productStorage.findAll();
  const videos = videoStorage.findAll();
  const rooms = liveStorage.findAll();

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

  // Views trend (mock daily data for last 14 days)
  const viewsTrend: { date: string; views: number; likes: number }[] = [];
  for (let i = 13; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    viewsTrend.push({
      date: key,
      views: Math.floor(totalViews / 14) + Math.floor(Math.random() * 200),
      likes: Math.floor(totalLikes / 14) + Math.floor(Math.random() * 50),
    });
  }

  res.json({
    kpi: { totalOrders, totalRevenue, totalProducts, totalVideos, totalViews, totalLikes, activeRooms },
    funnel: { pending, paid, completed, cancelled, total: orders.length },
    monthlyGMV,
    topProducts,
    viewsTrend,
  });
});

export default router;
