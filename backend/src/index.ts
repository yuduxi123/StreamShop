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
import path from 'path';

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

// Health check
app.get('/api/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`StreamShop API running on http://localhost:${PORT}`);
  console.log(`WebSocket server ready`);
});
