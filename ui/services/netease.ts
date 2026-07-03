import QRCode from 'qrcode';
import { Song, Playlist, NeteaseQrKeyResponse, NeteaseQrCreateResponse, NeteaseQrCheckResponse, NeteaseUserDetail, NeteaseDailyRecommendResponse } from '../types';

// 指向 mod 内置 LocalServer 的音乐代理（自实现，不再依赖外部托管 API）。
// 绝对地址：dev(:3000) 与 prod(:7781) 下都能命中 mod 的 HTTP 服务（已配 CORS）。
const BASE_URL = 'http://localhost:7781/api/netease';

let storedCookie = localStorage.getItem('netease_cookie') || '';


export const setCookie = (cookie: string) => {
  storedCookie = cookie;
  localStorage.setItem('netease_cookie', cookie);
};

export const getCookie = () => storedCookie;

export const clearCookie = () => {
    storedCookie = '';
    localStorage.removeItem('netease_cookie');
}

const request = async <T>(endpoint: string, options: RequestInit = {}): Promise<T> => {
  const url = new URL(`${BASE_URL}${endpoint}`);
  
  // Attach cookie if available
  // The API usually accepts cookie in query param
  if (storedCookie) {
    url.searchParams.append('cookie', storedCookie);
  }
  
  // Always append timestamp to prevent caching for most requests
  if (!url.searchParams.has('timestamp')) {
      url.searchParams.append('timestamp', Date.now().toString());
  }

  const res = await fetch(url.toString(), options);
  return res.json();
};

export const api = {
  getQrKey: () => request<NeteaseQrKeyResponse>('/login/qr/key'),
  
  // 客户端本地生成二维码图片（内容为网易云登录 codekey URL），无需后端渲染。
  createQr: async (key: string): Promise<NeteaseQrCreateResponse> => {
    const qrurl = `https://music.163.com/login?codekey=${key}`;
    const qrimg = await QRCode.toDataURL(qrurl, { width: 192, margin: 1 });
    return { code: 200, data: { qrurl, qrimg } };
  },
  
  checkQrStatus: (key: string, noCookie = false) => {
      let query = `/login/qr/check?key=${key}`;
      if (noCookie) {
          query += '&noCookie=true';
      }
      return request<NeteaseQrCheckResponse>(query);
  },
  
  getUserDetail: (uid: number) => request<NeteaseUserDetail>(`/user/detail?uid=${uid}`),
  
  getUserSubcount: () => request<any>('/user/subcount'),

  getLoginStatus: () => request<any>('/login/status'),

  getDailyRecommendSongs: () => request<NeteaseDailyRecommendResponse>('/recommend/songs'),

  getDailyRecommendedPlaylists: () => request<NeteaseRecommendResourceResponse>('/recommend/resource'),

  getUserPlaylists: (uid: number, limit = 30, offset = 0) => request<NeteaseUserPlaylistResponse>(`/user/playlist?uid=${uid}&limit=${limit}&offset=${offset}`),

  getPlaylistTracks: (id: number | string, limit = 100, offset = 0) => request<NeteasePlaylistTrackResponse>(`/playlist/track/all?id=${id}&limit=${limit}&offset=${offset}`),

  getSongUrl: (id: number | string, level = 'standard') => request<NeteaseSongUrlResponse>(`/song/url/v1?id=${id}&level=${level}`),

  getLyric: (id: number | string) => request<NeteaseLyricResponse>(`/lyric/new?id=${id}`),

  // 通知 mod 端清除持久化 cookie（登出）。
  logout: () => request<any>('/logout'),
};

const fmtDuration = (ms: number): string => {
  const s = Math.floor((ms || 0) / 1000);
  return `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, '0')}`;
};

/** 无登录也能用的推荐歌单（发现页）。 */
export const getNeteasePersonalized = async (limit = 12): Promise<Playlist[]> => {
  const res = await request<any>(`/personalized?limit=${limit}`);
  const result: any[] = res?.result || [];
  return result.map((p) => ({
    id: String(p.id), name: p.name, cover: p.picUrl || '', trackCount: p.trackCount,
    source: 'netease' as const, type: 'playlist' as const,
  }));
};

/** 推荐电台（电台页）。 */
export const getNeteaseRadios = async (limit = 12): Promise<Playlist[]> => {
  const res = await request<any>(`/dj/personalize?limit=${limit}`);
  const radios: any[] = res?.data || res?.djRadios || [];
  return radios.map((r) => ({
    id: String(r.id), name: r.name, cover: r.picUrl || '', trackCount: r.programCount,
    source: 'netease' as const, type: 'radio' as const,
  }));
};

/** 电台节目列表（作为可播放的“歌曲”）。 */
export const getNeteaseRadioPrograms = async (rid: string): Promise<Song[]> => {
  const res = await request<any>(`/dj/program?rid=${rid}&limit=100`);
  const programs: any[] = res?.programs || [];
  return programs.map((p) => {
    const s = p.mainSong || {};
    return {
      id: String(s.id ?? p.id),
      source: 'netease' as const,
      title: p.name || s.name || '',
      artist: (s.artists || []).map((a: any) => a.name).join(' / ') || p.dj?.brand || p.dj?.nickname || '',
      cover: p.coverUrl || s.album?.picUrl || '',
      duration: fmtDuration(s.duration || p.duration || 0),
    };
  });
};

/** 搜索网易云歌曲，映射为前端统一的 Song。 */
export const searchNetease = async (keyword: string, limit = 30): Promise<Song[]> => {
  const res = await request<any>(`/cloudsearch?keywords=${encodeURIComponent(keyword)}&limit=${limit}`);
  const songs: any[] = res?.result?.songs || [];
  return songs.map((s) => ({
    id: String(s.id),
    source: 'netease' as const,
    title: s.name,
    artist: (s.ar || []).map((a: any) => a.name).join(' / '),
    cover: s.al?.picUrl || '',
    duration: fmtDuration(s.dt || 0),
    vip: s.fee != null && s.fee !== 0,
  }));
};
