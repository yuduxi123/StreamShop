import {
  MediaUrlContext,
  normalizeProduct,
  normalizeUser,
  normalizeVideo,
  shouldExposeVideo,
} from '../services/media-url.service';

export interface VideoData {
  id: string;
  title: string;
  coverUrl: string;
  videoUrl: string;
  authorId: string;
  status: string;
  viewCount: number;
  likeCount: number;
  commentCount: number;
  shareCount: number;
  tags: string[];
  createdAt: string;
}

export interface VideoProduct {
  id: string;
  videoId: string;
  productId: string;
  displayOrder: number;
  timestampMs: number;
}

export interface LiveRoomData {
  id: string;
  title: string;
  coverUrl: string;
  streamUrl?: string;
  startedAt?: string | null;
  anchorId: string;
  status: string;
  currentProductId: string | null;
  onlineCount: number;
  likeCount: number;
  viewerCount: number;
  createdAt: string;
}

export interface LiveRoomProduct {
  id: string;
  liveRoomId: string;
  productId: string;
  displayOrder: number;
  isExplaining: boolean;
}

export type FeedItem =
  | { type: 'video'; id: string; createdAt: string; video: any }
  | { type: 'live'; id: string; createdAt: string; liveRoom: any };

interface BuildFeedInput {
  videos: VideoData[];
  liveRooms: LiveRoomData[];
  videoProducts: VideoProduct[];
  liveRoomProducts: LiveRoomProduct[];
  products: any[];
  users: any[];
  mediaBaseUrl?: string;
  streamBaseUrl?: string;
  uploadFileExists?: (filename: string) => boolean;
}

function createdAtMs(item: FeedItem): number {
  const parsed = Date.parse(item.createdAt);
  return Number.isNaN(parsed) ? 0 : parsed;
}

function livePriority(item: FeedItem): number {
  if (item.type !== 'live') return 0;
  return item.liveRoom?.status === 'live' ? 1 : 0;
}

function publicUser(user: any, mediaContext: MediaUrlContext): any {
  if (!user) return null;
  return normalizeUser({ id: user.id, username: user.username, avatarUrl: user.avatarUrl }, mediaContext);
}

export function buildFeedItems(input: BuildFeedInput): FeedItem[] {
  const {
    videos,
    liveRooms,
    videoProducts,
    liveRoomProducts,
    products,
    users,
    mediaBaseUrl,
    streamBaseUrl,
    uploadFileExists,
  } = input;
  const mediaContext: MediaUrlContext = { mediaBaseUrl: mediaBaseUrl || '', streamBaseUrl, uploadFileExists };
  const videoUrlSet = new Set(videos.map(video => video.videoUrl).filter(Boolean));

  const videoItems: FeedItem[] = videos
    .filter(video => video.status === 'published')
    .filter(video => shouldExposeVideo(video, mediaContext))
    .map(video => {
      const author = publicUser(users.find(user => user.id === video.authorId), mediaContext);
      const bindings = videoProducts
        .filter(binding => binding.videoId === video.id)
        .sort((a, b) => a.displayOrder - b.displayOrder);
      const boundProducts = bindings
        .map(binding => {
          const product = products.find(p => p.id === binding.productId);
          if (!product) return null;
          return { ...normalizeProduct(product, mediaContext), timestampMs: binding.timestampMs || 0 };
        })
        .filter(Boolean);
      return {
        type: 'video',
        id: video.id,
        createdAt: video.createdAt,
        video: normalizeVideo({ ...video, author, products: boundProducts }, mediaContext),
      };
    });

  const liveItems: FeedItem[] = liveRooms
    .filter(room => room.status === 'live')
    .map(room => {
      const anchor = publicUser(users.find(user => user.id === room.anchorId), mediaContext);
      const productBindings = liveRoomProducts
        .filter(binding => binding.liveRoomId === room.id)
        .sort((a, b) => a.displayOrder - b.displayOrder);
      const boundProducts = productBindings
        .map(binding => normalizeProduct(products.find(product => product.id === binding.productId), mediaContext))
        .filter(Boolean);
      const currentProduct = room.currentProductId
        ? normalizeProduct(products.find(product => product.id === room.currentProductId), mediaContext) || null
        : null;
      const streamUrl = room.streamUrl && !videoUrlSet.has(room.streamUrl)
        ? room.streamUrl
        : undefined;
      return {
        type: 'live',
        id: room.id,
        createdAt: room.createdAt,
        liveRoom: {
          ...room,
          streamUrl,
          startedAt: room.startedAt || room.createdAt,
          anchor,
          products: boundProducts,
          productBindings,
          currentProduct,
        },
      };
    });

  return [...videoItems, ...liveItems].sort((a, b) => {
    const timeDiff = createdAtMs(b) - createdAtMs(a);
    if (Math.abs(timeDiff) <= 5 * 60 * 1000) {
      const priorityDiff = livePriority(b) - livePriority(a);
      if (priorityDiff !== 0) return priorityDiff;
    }
    return timeDiff;
  });
}
