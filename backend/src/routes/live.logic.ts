export interface LiveStartRoom {
  id: string;
  status?: string;
  streamUrl?: string;
  onlineCount?: number;
  likeCount?: number;
}

export interface LiveRoomProductBinding {
  id: string;
  liveRoomId: string;
  productId: string;
  displayOrder?: number;
  isExplaining?: boolean;
}

export function buildLiveStreamUrl(serverIp: string, roomId: string): string {
  return `http://${serverIp}:8000/live/${roomId}.flv`;
}

export function resolveLiveStartUpdates(serverIp: string, room: LiveStartRoom): Partial<LiveStartRoom> {
  return {
    status: 'live',
    streamUrl: buildLiveStreamUrl(serverIp, room.id),
    onlineCount: 0,
    likeCount: 0,
  };
}

export function sortLiveRoomProductBindings<T extends LiveRoomProductBinding>(bindings: T[]): T[] {
  return [...bindings].sort((a, b) => {
    const orderDiff = productOrder(a) - productOrder(b);
    if (orderDiff !== 0) return orderDiff;
    return a.id.localeCompare(b.id);
  });
}

export function getNextLiveRoomProductDisplayOrder(bindings: LiveRoomProductBinding[]): number {
  if (bindings.length === 0) return 0;
  return Math.max(...bindings.map(productOrder)) + 1;
}

function productOrder(binding: LiveRoomProductBinding): number {
  return typeof binding.displayOrder === 'number' ? binding.displayOrder : 0;
}
