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
}

function publicUser(user: any): any {
  if (!user) return null;
  return { id: user.id, username: user.username, avatarUrl: user.avatarUrl };
}

function createdAtMs(item: FeedItem): number {
  const parsed = Date.parse(item.createdAt);
  return Number.isNaN(parsed) ? 0 : parsed;
}

function livePriority(item: FeedItem): number {
  if (item.type !== 'live') return 0;
  return item.liveRoom?.status === 'live' ? 1 : 0;
}

export function buildFeedItems(input: BuildFeedInput): FeedItem[] {
  const { videos, liveRooms, videoProducts, liveRoomProducts, products, users } = input;
  const videoUrlSet = new Set(videos.map(video => video.videoUrl).filter(Boolean));

  const videoItems: FeedItem[] = videos
    .filter(video => video.status === 'published')
    .map(video => {
      const author = publicUser(users.find(user => user.id === video.authorId));
      const bindings = videoProducts
        .filter(binding => binding.videoId === video.id)
        .sort((a, b) => a.displayOrder - b.displayOrder);
      const boundProducts = bindings
        .map(binding => products.find(product => product.id === binding.productId))
        .filter(Boolean);
      return {
        type: 'video',
        id: video.id,
        createdAt: video.createdAt,
        video: { ...video, author, products: boundProducts },
      };
    });

  const liveItems: FeedItem[] = liveRooms
    .filter(room => room.status === 'live')
    .map(room => {
      const anchor = publicUser(users.find(user => user.id === room.anchorId));
      const productBindings = liveRoomProducts
        .filter(binding => binding.liveRoomId === room.id)
        .sort((a, b) => a.displayOrder - b.displayOrder);
      const boundProducts = productBindings
        .map(binding => products.find(product => product.id === binding.productId))
        .filter(Boolean);
      const currentProduct = room.currentProductId
        ? products.find(product => product.id === room.currentProductId) || null
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
