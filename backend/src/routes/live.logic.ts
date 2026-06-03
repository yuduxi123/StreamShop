export interface LiveStartRoom {
  id: string;
  status?: string;
  streamUrl?: string;
  onlineCount?: number;
  likeCount?: number;
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
