import { Router, Request, Response } from 'express';
import { StorageService } from '../services/storage.service';
import {
  buildFeedItems,
  LiveRoomData,
  LiveRoomProduct,
  VideoData,
  VideoProduct,
} from './feed.logic';

const videoStorage = new StorageService<VideoData>('videos.json');
const liveStorage = new StorageService<LiveRoomData>('live_rooms.json');
const videoProductStorage = new StorageService<VideoProduct>('video_products.json');
const liveRoomProductStorage = new StorageService<LiveRoomProduct>('live_room_products.json');
const productStorage = new StorageService<any>('products.json');
const userStorage = new StorageService<any>('users.json');

const router = Router();

// GET /api/feed - mixed home feed for videos and live rooms
router.get('/', (_req: Request, res: Response) => {
  const page = Math.max(parseInt(_req.query.page as string) || 1, 1);
  const limit = Math.max(parseInt(_req.query.limit as string) || 10, 1);

  const items = buildFeedItems({
    videos: videoStorage.findAll(),
    liveRooms: liveStorage.findAll(),
    videoProducts: videoProductStorage.findAll(),
    liveRoomProducts: liveRoomProductStorage.findAll(),
    products: productStorage.findAll(),
    users: userStorage.findAll(),
  });

  const total = items.length;
  const start = (page - 1) * limit;
  res.json({
    data: items.slice(start, start + limit),
    total,
    page,
    limit,
  });
});

export default router;
