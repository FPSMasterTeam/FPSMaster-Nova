import { Song, Playlist } from '../types';

// QQ 音乐：调 mod 内置 LocalServer 的 /api/qq/*（返回本库规范化 JSON）。
const BASE_URL = 'http://localhost:7781/api/qq';

interface QqTrack {
  source: string;
  id: string;
  mid?: string;
  name: string;
  artists: string;
  album: string;
  durationMs: number;
  coverUrl?: string | null;
  vip: boolean;
}

export interface QqSongUrl {
  url: string | null;
  quality: string;
  format: string;
  isTrial: boolean;
  trialStartMs: number;
  trialEndMs: number;
  reason?: string | null;
}

export interface QqLyric {
  lrc: string;
  translated?: string | null;
}

export interface QqQrCode {
  key: string;
  qrContent: string; // data:image/png;base64,...
}

export interface QqUser {
  musicid: string;
  nickname: string;
  avatarUrl: string;
}

export interface QqQrCheck {
  state: 'WAITING' | 'SCANNED' | 'CONFIRMED' | 'EXPIRED' | 'ERROR';
  loggedIn: boolean;
  musicid: string;
}

const fmtDuration = (ms: number): string => {
  const s = Math.floor((ms || 0) / 1000);
  return `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, '0')}`;
};

const trackToSong = (t: QqTrack): Song => ({
  id: t.mid || t.id,
  mid: t.mid,
  source: 'qq',
  title: t.name,
  artist: t.artists,
  cover: t.coverUrl || '',
  duration: fmtDuration(t.durationMs),
  vip: t.vip,
});

export const qqApi = {
  search: async (keyword: string, limit = 30): Promise<Song[]> => {
    const res = await fetch(`${BASE_URL}/search?keyword=${encodeURIComponent(keyword)}&limit=${limit}`);
    const arr: QqTrack[] = await res.json();
    return Array.isArray(arr) ? arr.map(trackToSong) : [];
  },

  // 排行榜歌曲。topId 26 = 巅峰榜·热歌。
  getToplist: async (topId = 26, num = 30): Promise<Song[]> => {
    const res = await fetch(`${BASE_URL}/toplist?topId=${topId}&num=${num}`);
    const arr: QqTrack[] = await res.json();
    return Array.isArray(arr) ? arr.map(trackToSong) : [];
  },

  // 推荐歌单（发现页）。
  getRecommendPlaylists: async (size = 12): Promise<Playlist[]> => {
    const res = await fetch(`${BASE_URL}/recommend?size=${size}`);
    const arr: Array<{ id: string; name: string; coverUrl?: string; trackCount?: number }> = await res.json();
    return Array.isArray(arr) ? arr.map((p) => ({
      id: p.id, name: p.name, cover: p.coverUrl || '', trackCount: p.trackCount, source: 'qq' as const, type: 'playlist' as const,
    })) : [];
  },

  // 歌单内歌曲。
  getPlaylistTracks: async (id: string, num = 100): Promise<Song[]> => {
    const res = await fetch(`${BASE_URL}/playlist?id=${encodeURIComponent(id)}&num=${num}`);
    const arr: QqTrack[] = await res.json();
    return Array.isArray(arr) ? arr.map(trackToSong) : [];
  },

  getSongUrl: async (mid: string, quality = 'standard'): Promise<QqSongUrl> => {
    const res = await fetch(`${BASE_URL}/url?mid=${encodeURIComponent(mid)}&quality=${quality}`);
    return res.json();
  },

  getLyric: async (mid: string): Promise<QqLyric> => {
    const res = await fetch(`${BASE_URL}/lyric?mid=${encodeURIComponent(mid)}`);
    return res.json();
  },

  createQr: async (): Promise<QqQrCode> => {
    const res = await fetch(`${BASE_URL}/qr/create`);
    return res.json();
  },

  checkQr: async (key: string): Promise<QqQrCheck> => {
    const res = await fetch(`${BASE_URL}/qr/check?key=${encodeURIComponent(key)}`);
    return res.json();
  },

  // 手动 Cookie 登录：传入浏览器里的 uin(musicid) 和 qm_keyst(musickey)。
  loginWithCookie: async (musicid: string, musickey: string): Promise<{ loggedIn: boolean; musicid?: string; error?: string }> => {
    const res = await fetch(`${BASE_URL}/login/cookie?musicid=${encodeURIComponent(musicid)}&musickey=${encodeURIComponent(musickey)}`);
    return res.json();
  },

  // 查询 mod 端持久化的登录态（用于刷新/重启后恢复）。
  status: async (): Promise<{ loggedIn: boolean; musicid: string; user?: QqUser | null }> => {
    const res = await fetch(`${BASE_URL}/status`);
    return res.json();
  },

  getUser: async (): Promise<QqUser | null> => {
    const res = await fetch(`${BASE_URL}/user`);
    return res.json();
  },

  logout: async (): Promise<void> => {
    await fetch(`${BASE_URL}/logout`);
  },
};
