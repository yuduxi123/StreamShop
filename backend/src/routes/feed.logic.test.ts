import assert from 'assert';
import { buildFeedItems } from './feed.logic';

const users = [
  { id: 'anchor-1', username: 'alice', avatarUrl: 'https://example.com/a.png' },
  { id: 'author-1', username: 'bob', avatarUrl: 'https://example.com/b.png' },
];

const products = [
  { id: 'product-1', title: '耳机', price: 299, coverUrl: 'https://example.com/p.png' },
];

const videos = [
  {
    id: 'video-1',
    title: '公开视频',
    coverUrl: 'https://example.com/v.png',
    videoUrl: 'https://example.com/v.mp4',
    authorId: 'author-1',
    status: 'published',
    viewCount: 0,
    likeCount: 0,
    commentCount: 0,
    shareCount: 0,
    tags: ['数码'],
    createdAt: '2026-05-30T10:00:00Z',
  },
];

const videoProducts = [
  { id: 'video-1_product-1', videoId: 'video-1', productId: 'product-1', displayOrder: 0 },
];

const liveRooms = [
  {
    id: 'live-room',
    title: '直播中',
    coverUrl: 'https://example.com/live.png',
    streamUrl: 'https://example.com/live.m3u8',
    startedAt: '2026-05-30T11:00:00Z',
    anchorId: 'anchor-1',
    status: 'live',
    currentProductId: 'product-1',
    onlineCount: 10,
    likeCount: 100,
    viewerCount: 1000,
    createdAt: '2026-05-30T11:00:00Z',
  },
  {
    id: 'offline-room',
    title: '未开播',
    coverUrl: 'https://example.com/offline.png',
    streamUrl: 'https://example.com/offline.m3u8',
    startedAt: null,
    anchorId: 'anchor-1',
    status: 'offline',
    currentProductId: null,
    onlineCount: 0,
    likeCount: 0,
    viewerCount: 0,
    createdAt: '2026-05-30T12:00:00Z',
  },
  {
    id: 'legacy-live-room',
    title: '旧数据直播中',
    coverUrl: 'https://example.com/legacy.png',
    anchorId: 'anchor-1',
    status: 'live',
    currentProductId: null,
    onlineCount: 8,
    likeCount: 80,
    viewerCount: 800,
    createdAt: '2026-05-30T09:00:00Z',
  },
  {
    id: 'reused-video-live-room',
    title: '复用短视频的直播',
    coverUrl: 'https://example.com/reused.png',
    streamUrl: 'https://example.com/v.mp4',
    startedAt: '2026-05-30T08:00:00Z',
    anchorId: 'anchor-1',
    status: 'live',
    currentProductId: null,
    onlineCount: 6,
    likeCount: 60,
    viewerCount: 600,
    createdAt: '2026-05-30T08:00:00Z',
  },
];

const liveRoomProducts = [
  {
    id: 'live-room_product-1',
    liveRoomId: 'live-room',
    productId: 'product-1',
    displayOrder: 0,
    isExplaining: true,
  },
];

const items: any[] = buildFeedItems({
  videos,
  liveRooms,
  videoProducts,
  liveRoomProducts,
  products,
  users,
});

assert(items.some(item => item.type === 'video' && item.id === 'video-1'));
assert(items.some(item => item.type === 'live' && item.id === 'live-room'));
assert(!items.some(item => item.type === 'live' && item.id === 'offline-room'));

const liveItem = items.find(item => item.type === 'live' && item.id === 'live-room');
assert.equal(liveItem?.liveRoom.streamUrl, 'https://example.com/live.m3u8');
assert.equal(liveItem?.liveRoom.startedAt, '2026-05-30T11:00:00Z');

const legacyLiveItem = items.find(item => item.type === 'live' && item.id === 'legacy-live-room');
assert.equal(legacyLiveItem?.liveRoom.streamUrl, undefined);
assert.equal(legacyLiveItem?.liveRoom.startedAt, '2026-05-30T09:00:00Z');

const reusedVideoLiveItem = items.find(item => item.type === 'live' && item.id === 'reused-video-live-room');
assert.equal(reusedVideoLiveItem?.liveRoom.streamUrl, undefined);

console.log('feed.logic tests passed');
