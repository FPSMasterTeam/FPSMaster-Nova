import { NeteaseQrKeyResponse, NeteaseQrCreateResponse, NeteaseQrCheckResponse, NeteaseUserDetail, NeteaseDailyRecommendResponse } from '../types';

const BASE_URL = 'https://musicapi.skidder.top';

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
  
  createQr: (key: string) => request<NeteaseQrCreateResponse>(`/login/qr/create?key=${key}&qrimg=true`),
  
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
};
