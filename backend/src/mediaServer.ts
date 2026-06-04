import NodeMediaServer from 'node-media-server';
import { StorageService } from './services/storage.service';
import { buildLiveStreamUrl } from './routes/live.logic';

interface LiveRoomData {
  id: string;
  title: string;
  coverUrl: string;
  streamUrl?: string;
  anchorId: string;
  status: string;
  currentProductId: string | null;
  onlineCount: number;
  likeCount: number;
  viewerCount: number;
  createdAt: string;
}

const liveStorage = new StorageService<LiveRoomData>('live_rooms.json');

export function createMediaServer(serverIp: string): NodeMediaServer {
  const config = {
    rtmp: {
      port: 1935,
      chunk_size: 60000,
      gop_cache: true,
      ping: 30,
      ping_timeout: 60,
    },
    http: {
      port: 8000,
      mediaroot: './media',
      allow_origin: '*',
    },
    trans: {
      ffmpeg: process.env.FFMPEG_PATH || 'ffmpeg',
      tasks: [
        {
          app: 'live',
          ac: 'aac',
          hls: true,
          hlsFlags: '[hls_time=2:hls_list_size=3:hls_flags=delete_segments]',
          dash: true,
          dashFlags: '[f=dash:window_size=3:extra_window_size=5]',
        },
      ],
    },
  };

  const nms = new NodeMediaServer(config);

  // v4.x events: callback receives a single session object
  nms.on('prePublish', (session: any) => {
    console.log(`[MediaServer] Stream publish attempt: ${session?.streamPath || session?.streamName || 'unknown'}`);
  });

  nms.on('postPublish', (session: any) => {
    // v4 session object has: streamPath, streamApp, streamName, id
    const roomId = session?.streamName;
    console.log(`[MediaServer] Stream started: roomId=${roomId}, path=${session?.streamPath}`);

    if (roomId) {
      liveStorage.update(roomId, {
        status: 'live',
        streamUrl: buildLiveStreamUrl(serverIp, roomId),
      });
    }
  });

  nms.on('donePublish', (session: any) => {
    const roomId = session?.streamName;
    console.log(`[MediaServer] Stream ended: roomId=${roomId}`);

    if (roomId) {
      const room = liveStorage.findById(roomId);
      if (room && room.status === 'live') {
        liveStorage.update(roomId, { status: 'ended' });
      }
    }
  });

  return nms;
}
