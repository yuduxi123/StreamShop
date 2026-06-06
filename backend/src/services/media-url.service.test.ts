import assert from 'assert';
import {
  filterAvailableVideos,
  normalizeMediaFields,
  normalizeUploadUrl,
  normalizeVideo,
  shouldExposeVideo,
} from './media-url.service';

const context = {
  mediaBaseUrl: 'http://10.208.69.9:3000',
  streamBaseUrl: 'http://10.208.69.9:8000',
  uploadFileExists: (filename: string) => !filename.startsWith('missing.'),
};

assert.equal(
  normalizeUploadUrl('http://10.17.24.7:3000/uploads/a.jpg', context),
  'http://10.208.69.9:3000/uploads/a.jpg',
);

const normalized = normalizeMediaFields({
  avatarUrl: 'http://10.17.24.7:3000/uploads/avatar.jpg',
  live: {
    streamUrl: 'http://172.25.160.1:8000/live/room.flv',
  },
  items: [
    { coverUrl: 'http://10.17.24.7:3000/uploads/p.jpg' },
  ],
}, context) as any;

assert.equal(normalized.avatarUrl, 'http://10.208.69.9:3000/uploads/avatar.jpg');
assert.equal(normalized.live.streamUrl, 'http://10.208.69.9:8000/live/room.flv');
assert.equal(normalized.items[0].coverUrl, 'http://10.208.69.9:3000/uploads/p.jpg');

const visibleVideo = normalizeVideo({
  id: 'ok',
  coverUrl: 'http://10.17.24.7:3000/uploads/ok.jpg',
  videoUrl: 'http://10.17.24.7:3000/uploads/ok.mp4',
}, context) as any;

assert.equal(visibleVideo.videoUrl, 'http://10.208.69.9:3000/uploads/ok.mp4');
assert(shouldExposeVideo(visibleVideo, context));
assert(!shouldExposeVideo({
  id: 'missing',
  videoUrl: 'http://10.17.24.7:3000/uploads/missing.mp4',
}, context));
assert.equal(filterAvailableVideos([visibleVideo, { id: 'missing', videoUrl: 'http://10.17.24.7:3000/uploads/missing.mp4' }], context).length, 1);
assert.equal(
  (normalizeMediaFields({ coverUrl: 'http://10.17.24.7:3000/uploads/missing.jpg' }, context) as any).coverUrl,
  '',
);

console.log('media-url.service tests passed');
