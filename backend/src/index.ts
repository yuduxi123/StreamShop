import express from 'express';
import http from 'http';
import cors from 'cors';
import { WebSocketServer } from './websocket/wsServer';

// Route imports
import authRoutes from './routes/auth';
import videoRoutes from './routes/video';
import productRoutes from './routes/product';
import cartRoutes from './routes/cart';
import orderRoutes from './routes/order';
import commentRoutes from './routes/comment';
import liveRoutes from './routes/live';
import interactionRoutes from './routes/interaction';
import couponRoutes from './routes/coupon';
import recommendRoutes from './routes/recommend';
import statsRoutes from './routes/stats';
import flashsaleRoutes from './routes/flashsale';
import userRoutes from './routes/user';
import danmakuRoutes from './routes/danmaku';
import uploadRoutes from './routes/upload';
import messageRoutes from './routes/message';
import feedRoutes from './routes/feed';
import { createMediaServer } from './mediaServer';
import path from 'path';
import os from 'os';

const app = express();
const server = http.createServer(app);

// Middleware
app.use(cors());
app.use(express.json());

// Static file serving for uploads
app.use('/uploads', express.static(path.join(__dirname, '..', 'uploads')));

// WebSocket
const wss = new WebSocketServer(server);
app.locals.wss = wss;  // Make accessible to routes

// Routes
app.use('/api/auth', authRoutes);
app.use('/api/videos', videoRoutes);
app.use('/api/products', productRoutes);
app.use('/api/cart', cartRoutes);
app.use('/api/orders', orderRoutes);
app.use('/api/comments', commentRoutes);
app.use('/api/live', liveRoutes);
app.use('/api/interactions', interactionRoutes);
app.use('/api/coupons', couponRoutes);
app.use('/api/recommendations', recommendRoutes);
app.use('/api/stats', statsRoutes);
app.use('/api/flash-sales', flashsaleRoutes);
app.use('/api/users', userRoutes);
app.use('/api/danmaku', danmakuRoutes);
app.use('/api/upload', uploadRoutes);
app.use('/api/messages', messageRoutes);
app.use('/api/feed', feedRoutes);

// Health check
app.get('/api/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Get local IP for media server URLs
function getLocalIp(): string {
  const candidates: string[] = [];
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name] || []) {
      if (iface.family === 'IPv4' && !iface.internal) {
        candidates.push(iface.address);
      }
    }
  }

  const preferred = candidates.find(address => address.startsWith('10.'))
    || candidates.find(address => address.startsWith('192.168.'))
    || candidates.find(address => /^172\.(1[6-9]|2\d|3[0-1])\./.test(address));
  return preferred || candidates[0] || 'localhost';
}

const localIp = process.env.SERVER_IP || getLocalIp();
app.locals.serverIp = localIp;
const mediaServer = createMediaServer(localIp);

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`StreamShop API running on http://localhost:${PORT}`);
  console.log(`WebSocket server ready`);
  mediaServer.run();
  console.log(`Media server: RTMP on rtmp://${localIp}:1935/live, HTTP-FLV on http://${localIp}:8000/live`);
});
