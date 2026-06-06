import fs from 'fs';
import path from 'path';
import { NextFunction, Request, Response } from 'express';

const UPLOAD_DIR = path.join(__dirname, '..', '..', 'uploads');
const UPLOAD_MARKER = '/uploads/';
const LIVE_MARKER = '/live/';
const UPLOAD_URL_FIELDS = new Set([
  'avatarUrl',
  'coverUrl',
  'imageUrl',
  'lastMessageVideoCover',
  'thumbnailUrl',
  'videoCoverUrl',
  'videoUrl',
]);

export interface MediaUrlContext {
  mediaBaseUrl: string;
  streamBaseUrl?: string;
  uploadFileExists?: (filename: string) => boolean;
}

export function getRequestMediaContext(req: Request): MediaUrlContext {
  const host = req.get('host') || `localhost:${process.env.PORT || 3000}`;
  const mediaBaseUrl = `${req.protocol}://${host}`;
  const hostWithoutPort = host.split(':')[0];
  const serverIp = (req.app.locals.serverIp as string) || hostWithoutPort;
  return {
    mediaBaseUrl,
    streamBaseUrl: `http://${serverIp}:8000`,
    uploadFileExists: defaultUploadFileExists,
  };
}

export function normalizeJsonResponseMedia(req: Request, res: Response, next: NextFunction): void {
  const originalJson = res.json.bind(res);
  res.json = ((body?: any) => originalJson(normalizeMediaFields(body, getRequestMediaContext(req)))) as Response['json'];
  next();
}

export function defaultUploadFileExists(filename: string): boolean {
  if (!isSafeFilename(filename)) return false;
  return fs.existsSync(path.join(UPLOAD_DIR, filename));
}

export function uploadFilenameFromUrl(url?: string | null): string | null {
  if (!url) return null;
  const markerIndex = url.indexOf(UPLOAD_MARKER);
  if (markerIndex === -1) return null;
  const filename = url.slice(markerIndex + UPLOAD_MARKER.length).split(/[?#]/)[0];
  return isSafeFilename(filename) ? filename : null;
}

export function liveStreamFilenameFromUrl(url?: string | null): string | null {
  if (!url) return null;
  const markerIndex = url.indexOf(LIVE_MARKER);
  if (markerIndex === -1) return null;
  const filename = url.slice(markerIndex + LIVE_MARKER.length).split(/[?#]/)[0];
  return filename && !filename.includes('/') ? filename : null;
}

export function normalizeUploadUrl(url?: string | null, context?: MediaUrlContext): string | undefined {
  if (!url) return url || undefined;
  const filename = uploadFilenameFromUrl(url);
  if (!filename || !context?.mediaBaseUrl) return url;
  if (context.uploadFileExists && !context.uploadFileExists(filename)) return '';
  return `${trimTrailingSlash(context.mediaBaseUrl)}${UPLOAD_MARKER}${filename}`;
}

export function normalizeLiveStreamUrl(url?: string | null, context?: MediaUrlContext): string | undefined {
  if (!url) return url || undefined;
  const filename = liveStreamFilenameFromUrl(url);
  if (!filename || !context?.streamBaseUrl) return url;
  return `${trimTrailingSlash(context.streamBaseUrl)}${LIVE_MARKER}${filename}`;
}

export function normalizeMediaFields<T>(value: T, context: MediaUrlContext): T {
  return normalizeValue(undefined, value, context) as T;
}

export function normalizeUser<T>(user: T, context: MediaUrlContext): T {
  return normalizeMediaFields(user, context);
}

export function normalizeProduct<T>(product: T, context: MediaUrlContext): T {
  return normalizeMediaFields(product, context);
}

export function normalizeVideo<T>(video: T, context: MediaUrlContext): T {
  return normalizeMediaFields(video, context);
}

export function normalizeLiveRoom<T>(room: T, context: MediaUrlContext): T {
  return normalizeMediaFields(room, context);
}

export function shouldExposeVideo(video: any, context: MediaUrlContext): boolean {
  const filename = uploadFilenameFromUrl(video?.videoUrl);
  if (!filename || !context.uploadFileExists) return true;
  return context.uploadFileExists(filename);
}

export function filterAvailableVideos<T extends { videoUrl?: string }>(
  videos: T[],
  context: MediaUrlContext,
): T[] {
  return videos.filter(video => shouldExposeVideo(video, context));
}

function normalizeValue(key: string | undefined, value: any, context: MediaUrlContext): any {
  if (Array.isArray(value)) {
    if (key === 'memberAvatars') {
      return value.map(item => typeof item === 'string'
        ? normalizeUploadUrl(item, context) ?? item
        : normalizeValue(undefined, item, context));
    }
    return value.map(item => normalizeValue(undefined, item, context));
  }

  if (value && typeof value === 'object') {
    const result: any = {};
    for (const [childKey, childValue] of Object.entries(value)) {
      result[childKey] = normalizeValue(childKey, childValue, context);
    }
    return result;
  }

  if (typeof value !== 'string') return value;
  if (key === 'streamUrl') return normalizeLiveStreamUrl(value, context) ?? value;
  if (key && UPLOAD_URL_FIELDS.has(key)) return normalizeUploadUrl(value, context) ?? value;
  return value;
}

function trimTrailingSlash(value: string): string {
  return value.replace(/\/$/, '');
}

function isSafeFilename(filename: string): boolean {
  return Boolean(filename) && path.basename(filename) === filename;
}
