import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Search, Play, Pause, SkipForward, SkipBack, Heart, Disc, ArrowRight, List, Volume2, PlusCircle, MoreHorizontal, X, User, Loader2, RefreshCw, CheckCircle, LogOut, Shuffle, Repeat } from 'lucide-react';
import { Song, Playlist, NeteaseUserProfile, NeteaseUserPlaylistResponse } from '../types';
import { api, setCookie, clearCookie, searchNetease, getNeteasePersonalized, getNeteaseRadios, getNeteaseRadioPrograms } from '../services/netease';
import { qqApi, QqUser } from '../services/qq';
import { NetworkManager } from '../network/WebSocketClient';
import { PacketProcessor } from '../network/PacketProcessor';
import { ClientConfigPacket, ClientConfigRequestPacket, ClientConfigUpdatePacket } from '../network/packets/ClientConfigPackets';
import { useT } from '../i18n';

// 品牌图标（真实 logo）：网易云音乐来自 Simple Icons(CC0)，QQ音乐来自 Arcticons(FOSS)。
const NeteaseIcon = ({ size = 16 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <path d="M13.046 9.388a3.919 3.919 0 0 0-.66.19c-.809.312-1.447.991-1.666 1.775a2.269 2.269 0 0 0-.074.81c.048.546.333 1.05.764 1.35a1.483 1.483 0 0 0 2.01-.286c.406-.531.355-1.183.24-1.636-.098-.387-.22-.816-.345-1.249a64.76 64.76 0 0 1-.269-.954zm-.82 10.07c-3.984 0-7.224-3.24-7.224-7.223 0-.98.226-3.02 1.884-4.822A7.188 7.188 0 0 1 9.502 5.6a.792.792 0 1 1 .587 1.472 5.619 5.619 0 0 0-2.795 2.462 5.538 5.538 0 0 0-.707 2.7 5.645 5.645 0 0 0 5.638 5.638c1.844 0 3.627-.953 4.542-2.428 1.042-1.68.772-3.931-.627-5.238a3.299 3.299 0 0 0-1.437-.777c.172.589.334 1.18.494 1.772.284 1.12.1 2.181-.519 2.989-.39.51-.956.888-1.592 1.064a3.038 3.038 0 0 1-2.58-.44 3.45 3.45 0 0 1-1.44-2.514c-.04-.467.002-.93.128-1.376.35-1.256 1.356-2.339 2.622-2.826a5.5 5.5 0 0 1 .823-.246l-.134-.505c-.37-1.371.25-2.579 1.547-3.007.329-.109.68-.145 1.025-.105.792.09 1.476.592 1.709 1.023.258.507-.096 1.153-.706 1.153a.788.788 0 0 1-.54-.213c-.088-.08-.163-.174-.259-.247a.825.825 0 0 0-.632-.166.807.807 0 0 0-.634.551c-.056.191-.031.406.02.595.07.256.159.597.217.82 1.11.098 2.162.54 2.97 1.296 1.974 1.844 2.35 4.886.892 7.233-1.197 1.93-3.509 3.177-5.889 3.177zM0 12c0 6.627 5.373 12 12 12s12-5.373 12-12S18.627 0 12 0 0 5.373 0 12Z"/>
  </svg>
);

const QqMusicIcon = ({ size = 16 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 48 48" fill="none" stroke="currentColor" strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <circle cx="24" cy="24" r="21.5" />
    <path d="M33.235 8.5c-3.736 3.173-8.608 4.076-15.715.507l9.44 10.012c4.308 4.307 4.257 8.173 4.5 12.103c.266 4.296-1.537 6.736-4.067 7.805c-3.655 1.544-9.574-.164-11.681-3.526c-1.748-2.787-1.001-7.727 1.621-10.045c4.699-4.153 10.14-3.406 13.84 2.063" />
  </svg>
);

interface MusicPlayerProps {
    immersiveMode: boolean;
    setImmersiveMode: (enabled: boolean) => void;
}

const MUSIC_VOLUME_KEY = 'fpsmaster.musicVolume';
const DEFAULT_VOLUME = 75;

const readStoredVolume = () => {
  const rawValue = Number(localStorage.getItem(MUSIC_VOLUME_KEY));
  if (!Number.isFinite(rawValue)) {
    return DEFAULT_VOLUME;
  }

  return Math.min(100, Math.max(0, rawValue));
};

const clampVolume = (value: number) => Math.min(100, Math.max(0, value));

interface LyricWord {
    startTime: number;
    duration: number;
    text: string;
}

interface LyricLine {
    startTime: number;
    duration: number;
    text: string;
    words?: LyricWord[];
    translation?: string;
    isMetadata?: boolean;
}

const ScrollingText: React.FC<{ text: string; className?: string }> = ({ text, className }) => {
    const containerRef = useRef<HTMLDivElement>(null);
    const measureRef = useRef<HTMLSpanElement>(null);
    const [isOverflow, setIsOverflow] = useState(false);

    useEffect(() => {
        if (containerRef.current && measureRef.current) {
            setIsOverflow(measureRef.current.scrollWidth > containerRef.current.clientWidth);
        }
    }, [text]);

    return (
        <div ref={containerRef} className={`relative overflow-hidden ${className}`}>
            {/* Hidden measure element */}
            <span ref={measureRef} className="absolute opacity-0 pointer-events-none whitespace-nowrap">{text}</span>
            
            {isOverflow ? (
                <motion.div 
                    className="flex whitespace-nowrap"
                    animate={{ x: "-50%" }}
                    transition={{ 
                        duration: Math.max(text.length * 0.3, 5), 
                        ease: "linear", 
                        repeat: Infinity 
                    }}
                    style={{ width: 'fit-content' }}
                >
                    <span className="mr-12">{text}</span>
                    <span className="mr-12">{text}</span>
                </motion.div>
            ) : (
                <div className="truncate">{text}</div>
            )}
        </div>
    );
};

export const MusicPlayer: React.FC<MusicPlayerProps> = ({ immersiveMode, setImmersiveMode }) => {
  const t = useT();
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentSong, setCurrentSong] = useState<Song | null>(null);
  const [activeTab, setActiveTab] = useState<'discover' | 'library' | 'radio'>('discover');
  const [volume, setVolume] = useState(readStoredVolume);
  const [showQueue, setShowQueue] = useState(false);

  // --- Music Source (网易云 / QQ) ---
  const [musicSource, setMusicSource] = useState<'netease' | 'qq'>('netease');
  const [qqLoggedIn, setQqLoggedIn] = useState(false);
  const [qqUser, setQqUser] = useState<QqUser | null>(null);
  const [qqCookieUin, setQqCookieUin] = useState('');
  const [qqCookieKey, setQqCookieKey] = useState('');
  const [qqCookieError, setQqCookieError] = useState('');

  // --- Search State ---
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<Song[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [showSearchResults, setShowSearchResults] = useState(false);

  // --- Netease Login State ---
  const [userProfile, setUserProfile] = useState<NeteaseUserProfile | null>(null);
  const [showLogin, setShowLogin] = useState(false);
  const [loginSource, setLoginSource] = useState<'netease' | 'qq'>('netease');
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [qrKey, setQrKey] = useState('');
  const [qrImg, setQrImg] = useState('');
  const [qrStatus, setQrStatus] = useState(0); // 800:expired, 801:waiting, 802:scanned, 803:success
  const loginCheckInterval = useRef<NodeJS.Timeout | null>(null);

  // --- Daily Recommend State ---
  const [dailySongs, setDailySongs] = useState<Song[]>([]);
  const [recommendedPlaylists, setRecommendedPlaylists] = useState<Playlist[]>([]);
  const [userPlaylists, setUserPlaylists] = useState<Playlist[]>([]);
  const [discoverPlaylists, setDiscoverPlaylists] = useState<Playlist[]>([]); // 发现页歌单（按来源）
  const [radios, setRadios] = useState<Playlist[]>([]); // 电台（网易云）

  // --- Playlist State ---
  const [currentPlaylist, setCurrentPlaylist] = useState<Playlist | null>(null); // 进入的歌单/电台详情
  const [playlistSongs, setPlaylistSongs] = useState<Song[]>([]);
  const [isLoadingPlaylist, setIsLoadingPlaylist] = useState(false);

  // --- Audio State ---
  const audioRef = useRef<HTMLAudioElement>(null);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isTrial, setIsTrial] = useState(false); // 当前曲目是否为试听片段
  const [playbackNotice, setPlaybackNotice] = useState(''); // 不可播放原因提示
  const autoSkipRef = useRef(0); // 连续自动跳过计数（防死循环）
  const [lyric, setLyric] = useState<string>('');
  const [parsedLyrics, setParsedLyrics] = useState<LyricLine[]>([]);
  const [currentLineIndex, setCurrentLineIndex] = useState(0);
  const lyricContainerRef = useRef<HTMLDivElement>(null);
  const remoteVolumeReady = useRef(false);

  useEffect(() => {
    const handleClientConfig = (packet: ClientConfigPacket) => {
      remoteVolumeReady.current = true;
      setVolume(clampVolume(packet.musicVolume));
    };

    PacketProcessor.register(16, handleClientConfig);
    NetworkManager.send(new ClientConfigRequestPacket());
    return () => {
      PacketProcessor.unregister(16, handleClientConfig);
    };
  }, []);

  const parseLyrics = (yrc?: string, lrc?: string, tlyric?: string) => {
    // Helper to parse standard LRC timestamp [mm:ss.xx]
    const parseLrcTime = (timeStr: string): number => {
        const parts = timeStr.split(':');
        const min = parseInt(parts[0]);
        const sec = parseFloat(parts[1]);
        return (min * 60 + sec) * 1000;
    };

    let lines: LyricLine[] = [];

    // Priority: YRC -> LRC
    if (yrc) {
        const rawLines = yrc.split('\n');
        for (const line of rawLines) {
            if (!line.trim()) continue;
            
            // Check if metadata JSON
            if (line.trim().startsWith('{')) {
                try {
                   const meta = JSON.parse(line);
                   if (meta.c && Array.isArray(meta.c)) {
                       const text = meta.c.map((item: any) => item.tx || '').join('');
                       lines.push({
                           startTime: meta.t || 0,
                           duration: 0,
                           text: text,
                           isMetadata: true
                       });
                   }
                } catch (e) {}
                continue;
            }

            // Check if YRC line
            // [start,duration](wordStart,wordDuration,?)Word...
            const lineMatch = line.match(/^\[(\d+),(\d+)\](.*)$/);
            if (lineMatch) {
                const startTime = parseInt(lineMatch[1]);
                const duration = parseInt(lineMatch[2]);
                const rest = lineMatch[3];
                
                const words: LyricWord[] = [];
                const wordRegex = /\((\d+),(\d+),(\d+)\)([^\(]+)/g;
                let wordMatch;
                let fullText = '';
                
                while ((wordMatch = wordRegex.exec(rest)) !== null) {
                    const wStart = parseInt(wordMatch[1]);
                    const wDur = parseInt(wordMatch[2]);
                    const wText = wordMatch[4];
                    words.push({
                        startTime: wStart,
                        duration: wDur,
                        text: wText
                    });
                    fullText += wText;
                }
                
                if (!fullText && rest) {
                     fullText = rest;
                }

                lines.push({
                    startTime,
                    duration,
                    text: fullText,
                    words
                });
            }
        }
    } else if (lrc) {
        const rawLines = lrc.split('\n');
        for (const line of rawLines) {
           const match = line.match(/^\[(\d{2}):(\d{2}\.\d{2,3})\](.*)$/);
           if (match) {
               const startTime = parseLrcTime(`${match[1]}:${match[2]}`);
               lines.push({
                   startTime,
                   duration: 0,
                   text: match[3]
               });
           }
        }
    }

    // Sort lines by time
    lines.sort((a, b) => a.startTime - b.startTime);

    // Fill duration for LRC if needed
    if (!yrc && lines.length > 0) {
        for (let i = 0; i < lines.length - 1; i++) {
            lines[i].duration = lines[i+1].startTime - lines[i].startTime;
        }
    }

    // Parse translations
    if (tlyric) {
         const tLines = tlyric.split('\n');
         const translationMap = new Map<number, string>();
         
         for (const line of tLines) {
             const match = line.match(/^\[(\d{2}):(\d{2}\.\d{2,3})\](.*)$/);
             if (match) {
                 const time = parseLrcTime(`${match[1]}:${match[2]}`);
                 translationMap.set(time, match[3]);
             }
         }

         // Match translations to lines
         for (const line of lines) {
             if (translationMap.has(line.startTime)) {
                 line.translation = translationMap.get(line.startTime);
             } else {
                 // Find closest within margin
                 for (const [tTime, tText] of translationMap.entries()) {
                     if (Math.abs(tTime - line.startTime) < 500) {
                         line.translation = tText;
                         break;
                     }
                 }
             }
         }
    }

    setParsedLyrics(lines);
  };

  // Initial scroll when immersive mode is opened
  useEffect(() => {
      if (immersiveMode && lyricContainerRef.current && parsedLyrics.length > 0) {
          const activeEl = lyricContainerRef.current.children[currentLineIndex] as HTMLElement;
          if (activeEl) {
              const container = lyricContainerRef.current;
              const offset = activeEl.offsetTop - container.clientHeight * 0.35 + activeEl.clientHeight / 2;
              container.scrollTo({ top: offset, behavior: 'instant' }); // Instant for initial open
          }
      }
  }, [immersiveMode]);

  // Sync lyric index
  useEffect(() => {
      const timeMs = currentTime * 1000;
      let activeIndex = -1;
      for (let i = 0; i < parsedLyrics.length; i++) {
          if (timeMs >= parsedLyrics[i].startTime) {
              activeIndex = i;
          } else {
              break;
          }
      }
      if (activeIndex !== -1 && activeIndex !== currentLineIndex) {
          setCurrentLineIndex(activeIndex);
          // Auto scroll
          if (immersiveMode && lyricContainerRef.current) {
              const activeEl = lyricContainerRef.current.children[activeIndex] as HTMLElement;
              if (activeEl) {
                  // Use scrollTo instead of scrollIntoView to prevent parent scrolling
                  // Adjust offset to be slightly higher (subtract less from offsetTop, or rather target a position higher in viewport)
                  // Center: offset = activeEl.offsetTop - container.clientHeight / 2 + activeEl.clientHeight / 2;
                  // Higher (35% from top): offset = activeEl.offsetTop - container.clientHeight * 0.35 + activeEl.clientHeight / 2;
                  const container = lyricContainerRef.current;
                  const offset = activeEl.offsetTop - container.clientHeight * 0.35 + activeEl.clientHeight / 2;
                  container.scrollTo({ top: offset, behavior: 'smooth' });
              }
          }
      }
  }, [currentTime, parsedLyrics, immersiveMode]);


  // Check login status on mount (登录态由 mod 端持久化，重启/刷新后自动恢复)
  useEffect(() => {
    // 网易云：无条件查询，后端会用持久化的 cookie 兜底
    const checkNetease = async () => {
      try {
        const res = await api.getLoginStatus();
        if (res.data?.profile) {
          setUserProfile(res.data.profile);
          fetchDailySongs();
          fetchRecommendedPlaylists();
          fetchUserPlaylists(res.data.profile.userId);
        }
      } catch (e) {
        console.error("Failed to check netease login status", e);
      }
    };
    // QQ：查询 mod 端持久化登录态
    const checkQq = async () => {
      try {
        const s = await qqApi.status();
        if (s.loggedIn) {
          setQqLoggedIn(true);
          if (s.user) setQqUser(s.user);
        }
      } catch (e) {
        console.error("Failed to check qq login status", e);
      }
    };
    checkNetease();
    checkQq();
  }, []);

  const fetchDailySongs = async () => {
      try {
          const res = await api.getDailyRecommendSongs();
          if (res.code === 200) {
              const songs: Song[] = res.data.dailySongs.map(s => ({
                  id: s.id.toString(),
                  title: s.name,
                  artist: s.ar.map(a => a.name).join('/'),
                  cover: s.al.picUrl,
                  duration: formatDuration(s.dt)
              }));
              setDailySongs(songs);
          }
      } catch (e) {
          console.error("Failed to fetch daily songs", e);
      }
  };

  const fetchRecommendedPlaylists = async () => {
      try {
          const res = await api.getDailyRecommendedPlaylists();
          if (res.code === 200) {
              const playlists: Playlist[] = res.recommend.map(p => ({
                  id: p.id.toString(),
                  name: p.name,
                  cover: p.picUrl,
                  trackCount: p.trackCount
              }));
              setRecommendedPlaylists(playlists);
          }
      } catch (e) {
          console.error("Failed to fetch recommended playlists", e);
      }
  };

  const fetchUserPlaylists = async (uid: number) => {
      try {
          const res = await api.getUserPlaylists(uid);
          if (res.code === 200) {
              const playlists: Playlist[] = res.playlist.map(p => ({
                  id: p.id.toString(),
                  name: p.name,
                  cover: p.coverImgUrl,
                  trackCount: p.trackCount
              }));
              setUserPlaylists(playlists);
          }
      } catch (e) {
          console.error("Failed to fetch user playlists", e);
      }
  };

  // 网易云歌单歌曲
  const fetchNeteasePlaylistSongs = async (id: string): Promise<Song[]> => {
      const res = await api.getPlaylistTracks(id);
      if (res.code !== 200) return [];
      return res.songs.map(s => ({
          id: s.id.toString(),
          source: 'netease' as const,
          title: s.name,
          artist: s.ar.map(a => a.name).join('/'),
          cover: s.al.picUrl,
          duration: formatDuration(s.dt),
      }));
  };

  // 进入歌单/电台详情，加载其歌曲
  const openPlaylist = async (playlist: Playlist, autoPlay = false) => {
      setCurrentPlaylist(playlist);
      setShowSearchResults(false);
      // 每日推荐：直接用已加载的日推歌曲，不用请求
      if (playlist.id === 'daily') {
          setPlaylistSongs(dailySongs);
          if (autoPlay && dailySongs[0]) setCurrentSong(dailySongs[0]);
          return;
      }
      setPlaylistSongs([]);
      setIsLoadingPlaylist(true);
      try {
          let songs: Song[] = [];
          if (playlist.source === 'qq') {
              songs = await qqApi.getPlaylistTracks(playlist.id);
          } else if (playlist.type === 'radio') {
              songs = await getNeteaseRadioPrograms(playlist.id);
          } else {
              songs = await fetchNeteasePlaylistSongs(playlist.id);
          }
          setPlaylistSongs(songs);
          if (autoPlay && songs.length > 0) setCurrentSong(songs[0]);
      } catch (e) {
          console.error("Failed to open playlist", e);
      } finally {
          setIsLoadingPlaylist(false);
      }
  };

  // 加载发现页歌单（按当前来源）
  const loadDiscover = async (src: 'netease' | 'qq') => {
      try {
          const pls = src === 'qq' ? await qqApi.getRecommendPlaylists() : await getNeteasePersonalized();
          setDiscoverPlaylists(pls);
      } catch (e) { console.error("loadDiscover failed", e); }
  };

  const loadRadios = async () => {
      try { setRadios(await getNeteaseRadios()); } catch (e) { console.error("loadRadios failed", e); }
  };


  // 发现页随来源变化加载
  useEffect(() => { loadDiscover(musicSource); }, [musicSource]);
  // 切到电台页时加载（仅网易云）
  useEffect(() => {
      if (activeTab === 'radio' && musicSource === 'netease' && radios.length === 0) loadRadios();
  }, [activeTab, musicSource]);
  // QQ 无电台/我的，自动回到发现
  useEffect(() => {
      if (musicSource === 'qq' && activeTab !== 'discover') setActiveTab('discover');
  }, [musicSource, activeTab]);

  // 歌单/电台卡片网格：点击进详情，右下角播放键直接播放全部
  const renderPlaylistGrid = (list: Playlist[], emptyText: string) => (
      <div className="grid grid-cols-4 gap-4">
          {list.map(p => (
              <div key={p.id} className="group cursor-pointer" onClick={() => openPlaylist(p)}>
                  <div className="relative aspect-square rounded-xl overflow-hidden mb-2 shadow-md bg-neutral-800">
                      {p.cover && <img src={p.cover} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />}
                      <button
                          onClick={(e) => { e.stopPropagation(); openPlaylist(p, true); }}
                          className="absolute bottom-2 right-2 w-9 h-9 rounded-full bg-indigo-600 hover:bg-indigo-500 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all shadow-lg translate-y-1 group-hover:translate-y-0"
                          title={t('music.playAll')}
                      >
                          <Play size={15} fill="white" className="ml-0.5 text-white" />
                      </button>
                  </div>
                  <p className="text-xs font-semibold text-neutral-200 truncate group-hover:text-white transition-colors">{p.name}</p>
                  {!!p.trackCount && <p className="text-[10px] text-neutral-500">{p.trackCount} {t('music.songs')}</p>}
              </div>
          ))}
          {list.length === 0 && (
              <div className="col-span-4 text-center py-12 text-neutral-500 text-sm border border-white/5 rounded-xl">{emptyText}</div>
          )}
      </div>
  );

  // 歌曲行列表（歌单详情 / 搜索结果共用）
  const renderSongRows = (songs: Song[]) => (
      <div className="space-y-1">
          {songs.map((song, i) => (
              <div
                  key={song.id + '-' + i}
                  onClick={() => setCurrentSong(song)}
                  className={`flex items-center gap-4 p-3 rounded-xl transition-all cursor-pointer group ${
                      currentSong?.id === song.id
                          ? 'bg-gradient-to-r from-indigo-500/20 to-transparent border border-indigo-500/20'
                          : 'hover:bg-white/5 border border-transparent'
                  }`}
              >
                  <span className="w-6 text-center text-xs text-neutral-500 font-mono group-hover:text-white">{i + 1}</span>
                  <img src={song.cover} className="w-10 h-10 rounded-lg object-cover shadow-sm bg-neutral-800" />
                  <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-1.5">
                          <h4 className={`text-sm font-medium truncate ${currentSong?.id === song.id ? 'text-indigo-300' : 'text-white'}`}>{song.title}</h4>
                          {song.vip && <span className="shrink-0 px-1 py-px rounded text-[9px] font-bold bg-amber-500/20 text-amber-400 border border-amber-500/30 leading-none">VIP</span>}
                      </div>
                      <p className="text-xs text-neutral-500 truncate group-hover:text-neutral-400">{song.artist}</p>
                  </div>
                  <span className="text-xs text-neutral-600 font-mono group-hover:text-neutral-500">{song.duration}</span>
              </div>
          ))}
          {songs.length === 0 && (
              <div className="text-center py-10 text-neutral-500 text-sm border border-white/5 rounded-xl">
                  {(isLoadingPlaylist || isSearching)
                      ? <span className="flex items-center justify-center gap-2"><Loader2 className="animate-spin" size={16} /> {t('music.loadingPlaylist')}</span>
                      : t('music.noSearchResults')}
              </div>
          )}
      </div>
  );

  const formatDuration = (ms: number) => {
      const totalSeconds = Math.floor(ms / 1000);
      const minutes = Math.floor(totalSeconds / 60);
      const seconds = totalSeconds % 60;
      return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  };

  // --- Search ---
  const doSearch = async () => {
    const q = searchQuery.trim();
    if (!q) { setShowSearchResults(false); setSearchResults([]); return; }
    setIsSearching(true);
    setShowSearchResults(true);
    try {
      const results = musicSource === 'qq' ? await qqApi.search(q) : await searchNetease(q);
      setSearchResults(results);
    } catch (e) {
      console.error("Search failed", e);
      setSearchResults([]);
    } finally {
      setIsSearching(false);
    }
  };

  const switchSource = (src: 'netease' | 'qq') => {
    if (src === musicSource) return;
    setMusicSource(src);
    setShowSearchResults(false);
    setSearchResults([]);
    setSearchQuery('');
    setCurrentPlaylist(null);
    setDiscoverPlaylists([]);
    if (src === 'qq') setActiveTab('discover');
    // 发现页数据由 musicSource 的 useEffect 自动加载
  };

  // Handle Login Flow
  const initLogin = async () => {
    if (musicSource === 'qq') return initQqLogin();
    try {
      setLoginSource('netease');
      setQrStatus(0);
      const keyRes = await api.getQrKey();
      if (keyRes.code === 200) {
        setQrKey(keyRes.data.unikey);
        const createRes = await api.createQr(keyRes.data.unikey);
        if (createRes.code === 200) {
          setQrImg(createRes.data.qrimg);
          setShowLogin(true);
        }
      }
    } catch (e) {
      console.error("Login init failed", e);
    }
  };

  // QQ 登录：扫码（对齐当前可用实现，已修复空值覆盖 cookie 的 bug）+ 手动 Cookie 兜底。
  const initQqLogin = async () => {
    setLoginSource('qq');
    setQqCookieUin('');
    setQqCookieKey('');
    setQqCookieError('');
    setQrKey('');
    setQrImg('');
    setQrStatus(0);
    setShowLogin(true);
    try {
      const qr = await qqApi.createQr();
      if (qr.key && qr.qrContent) {
        setQrKey(qr.key);
        setQrImg(qr.qrContent);
      }
    } catch (e) {
      console.error("QQ QR init failed", e);
    }
  };

  const handleQqCookieLogin = async () => {
    setQqCookieError('');
    if (!qqCookieUin.trim() || !qqCookieKey.trim()) {
      setQqCookieError(t('music.qqCookieMissing'));
      return;
    }
    try {
      const res = await qqApi.loginWithCookie(qqCookieUin.trim(), qqCookieKey.trim());
      if (res.loggedIn) {
        setQqLoggedIn(true);
        setShowLogin(false);
        qqApi.getUser().then((u) => u && setQqUser(u)).catch(() => {});
      } else {
        setQqCookieError(res.error || t('music.qqCookieFailed'));
      }
    } catch (e) {
      console.error("QQ cookie login failed", e);
      setQqCookieError(t('music.qqCookieFailed'));
    }
  };

  // Poll QR Status
  useEffect(() => {
    if (!showLogin || !qrKey) return;

    // QQ 扫码：state 映射到与网易云一致的 qrStatus（801等待/802已扫/803成功/800过期）
    if (loginSource === 'qq') {
      if (qqLoggedIn) return;
      loginCheckInterval.current = setInterval(async () => {
        try {
          if (qrStatus === 800) return;
          const r = await qqApi.checkQr(qrKey);
          const map: Record<string, number> = { WAITING: 801, SCANNED: 802, CONFIRMED: 803, EXPIRED: 800, ERROR: 800 };
          setQrStatus(map[r.state] ?? 801);
          if (r.state === 'EXPIRED' || r.state === 'ERROR') {
            clearInterval(loginCheckInterval.current!);
          } else if (r.state === 'CONFIRMED' && r.loggedIn) {
            clearInterval(loginCheckInterval.current!);
            setQqLoggedIn(true);
            setShowLogin(false);
            qqApi.getUser().then((u) => u && setQqUser(u)).catch(() => {});
          }
        } catch (e) {
          console.error("QQ check status failed", e);
        }
      }, 2000);
      return () => { if (loginCheckInterval.current) clearInterval(loginCheckInterval.current); };
    }

    // 网易云扫码
    if (!userProfile) {
      loginCheckInterval.current = setInterval(async () => {
        try {
          // If status is 800 (expired), stop polling? or let user refresh
          if (qrStatus === 800) return;

          let res = await api.checkQrStatus(qrKey);
          if (res.code === 502) {
             res = await api.checkQrStatus(qrKey, true);
          }

          setQrStatus(res.code);

          if (res.code === 800) {
              // Expired
              clearInterval(loginCheckInterval.current!);
          } else if (res.code === 803) {
             // Success
             clearInterval(loginCheckInterval.current!);
             setCookie(res.cookie);
             // Fetch profile
             const statusRes = await api.getLoginStatus();
             if (statusRes.data?.profile) {
               setUserProfile(statusRes.data.profile);
               setShowLogin(false);
               fetchDailySongs();
               fetchRecommendedPlaylists();
               fetchUserPlaylists(statusRes.data.profile.userId);
             }
          }
        } catch (e) {
          console.error("Check status failed", e);
        }
      }, 2000);
    }

    return () => {
      if (loginCheckInterval.current) clearInterval(loginCheckInterval.current);
    };
  }, [showLogin, qrKey, userProfile, qrStatus, loginSource, qqLoggedIn]);

  // --- Playback Logic ---
  useEffect(() => {
    if (currentSong) {
        // Immediate reset on song change
        if (audioRef.current) {
            audioRef.current.pause();
        }
        setIsPlaying(false);
        setCurrentTime(0);
        setDuration(0);
        setLyric('');
        setParsedLyrics([]);
        setCurrentLineIndex(0);

        const startPlayback = (url: string | null | undefined) => {
            if (url && audioRef.current) {
                audioRef.current.src = url;
                // Use a small timeout to ensure DOM is ready and prevent race conditions
                setTimeout(async () => {
                    try {
                        await audioRef.current?.play();
                        setIsPlaying(true);
                        autoSkipRef.current = 0; // 播放成功，重置跳过计数
                        setPlaybackNotice('');
                    } catch (e) {
                        console.error("Play failed", e);
                    }
                }, 100);
            } else {
                console.warn("No URL found for song", currentSong.title);
            }
        };

        // 无法播放：显示原因，并自动切到下一首（带连续失败上限，防死循环）
        const handleUnavailable = (reason: string) => {
            setIsPlaying(false);
            setPlaybackNotice(`《${currentSong.title}》${reason}`);
            const limit = Math.min(displaySongs.length || 1, 15);
            if (autoSkipRef.current < limit) {
                autoSkipRef.current += 1;
                setTimeout(() => playNext(), 1500);
            } else {
                autoSkipRef.current = 0;
                setPlaybackNotice('连续多首无法播放，已停止自动切换');
            }
        };

        const fetchData = async () => {
            try {
                if (currentSong.source === 'qq' && currentSong.mid) {
                    // QQ 音乐
                    const u = await qqApi.getSongUrl(currentSong.mid);
                    setIsTrial(!!u.isTrial);
                    if (u.url) startPlayback(u.url);
                    else handleUnavailable(u.reason || '无法播放');
                    const ly = await qqApi.getLyric(currentSong.mid);
                    setLyric(ly.lrc || '');
                    parseLyrics(undefined, ly.lrc || '', ly.translated || undefined);
                } else {
                    // 网易云
                    const res = await api.getSongUrl(currentSong.id);
                    setIsTrial(!!res.data?.[0]?.freeTrialInfo);
                    const neUrl = res.code === 200 ? res.data?.[0]?.url : null;
                    if (neUrl) startPlayback(neUrl);
                    else handleUnavailable(res.data?.[0]?.freeTrialInfo ? '仅 VIP 可听完整版' : 'VIP 或无版权，无法播放');
                    const lrcRes = await api.getLyric(currentSong.id);
                    if (lrcRes.code === 200) {
                        setLyric(lrcRes.lrc?.lyric || '');
                        parseLyrics(lrcRes.yrc?.lyric, lrcRes.lrc?.lyric, lrcRes.tlyric?.lyric);
                    } else {
                        setLyric('');
                        setParsedLyrics([]);
                    }
                }
            } catch (e) {
                console.error("Failed to fetch song data", e);
            }
        };
        fetchData();

        // Update Media Session Metadata
        if ('mediaSession' in navigator) {
            navigator.mediaSession.metadata = new MediaMetadata({
                title: currentSong.title,
                artist: currentSong.artist,
                artwork: [
                    { src: currentSong.cover, sizes: '512x512', type: 'image/jpeg' }
                ]
            });
        }
    }
  }, [currentSong]);

  useEffect(() => {
    if (audioRef.current) {
        if (isPlaying) {
            audioRef.current.play().catch(e => console.error("Play failed", e));
        } else {
            audioRef.current.pause();
        }
        if ('mediaSession' in navigator) {
            navigator.mediaSession.playbackState = isPlaying ? 'playing' : 'paused';
        }
    }
  }, [isPlaying]);

  useEffect(() => {
    if (audioRef.current) {
        audioRef.current.volume = volume / 100;
    }
    localStorage.setItem(MUSIC_VOLUME_KEY, String(volume));
    if (remoteVolumeReady.current) {
        NetworkManager.send(new ClientConfigUpdatePacket(volume));
    }
  }, [volume]);

  const handleTimeUpdate = () => {
      if (audioRef.current) {
          setCurrentTime(audioRef.current.currentTime);
          setDuration(audioRef.current.duration || 0);
      }
  };

  const handleEnded = () => {
      setIsPlaying(false);
      playNext();
  };

  // 提示消息 5 秒后自动消失
  useEffect(() => {
      if (!playbackNotice) return;
      const t = setTimeout(() => setPlaybackNotice(''), 5000);
      return () => clearTimeout(t);
  }, [playbackNotice]);

  const playNext = () => {
      const currentIndex = displaySongs.findIndex(s => s.id === currentSong?.id);
      if (currentIndex !== -1 && currentIndex < displaySongs.length - 1) {
          setCurrentSong(displaySongs[currentIndex + 1]);
      } else if (displaySongs.length > 0) {
           setCurrentSong(displaySongs[0]);
      }
  };

  const playPrev = () => {
      const currentIndex = displaySongs.findIndex(s => s.id === currentSong?.id);
      if (currentIndex !== -1 && currentIndex > 0) {
          setCurrentSong(displaySongs[currentIndex - 1]);
      } else if (displaySongs.length > 0) {
          setCurrentSong(displaySongs[displaySongs.length - 1]);
      }
  };

  const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
      const time = Number(e.target.value);
      if (audioRef.current) {
          audioRef.current.currentTime = time;
          setCurrentTime(time);
      }
  };

  const handleLogout = () => {
      clearCookie();
      api.logout().catch(() => {}); // 清除 mod 端持久化 cookie
      setUserProfile(null);
      setDailySongs([]);
      setRecommendedPlaylists([]);
      setUserPlaylists([]);
      setShowLogoutConfirm(false);
  };

  const handleQqLogout = () => {
      qqApi.logout().catch(() => {}); // 清除 mod 端持久化 QQ 凭证
      setQqLoggedIn(false);
      setQqUser(null);
  };

  // Display songs: either playlist songs (if selected), daily songs (if logged in and fetched) or empty
  // 播放队列上下文：搜索结果 或 当前歌单/电台的歌曲
  const displaySongs = showSearchResults ? searchResults : (currentPlaylist ? playlistSongs : []);

  // --- SMTC Media Session Handlers ---
  useEffect(() => {
      if ('mediaSession' in navigator) {
          navigator.mediaSession.setActionHandler('play', () => setIsPlaying(true));
          navigator.mediaSession.setActionHandler('pause', () => setIsPlaying(false));
          navigator.mediaSession.setActionHandler('previoustrack', playPrev);
          navigator.mediaSession.setActionHandler('nexttrack', playNext);
      }
  }, [playNext, playPrev]);

  return (
    <div className="relative h-full flex flex-col bg-black/20 overflow-hidden">
      
      {/* Login Modal */}
      <AnimatePresence>
        {showLogin && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
             <motion.div 
               initial={{ opacity: 0, scale: 0.95 }}
               animate={{ opacity: 1, scale: 1 }}
               exit={{ opacity: 0, scale: 0.95 }}
               className="bg-neutral-900 border border-white/10 rounded-2xl p-6 w-full max-w-sm shadow-2xl relative"
             >
                <button 
                  onClick={() => setShowLogin(false)}
                  className="absolute top-4 right-4 text-neutral-500 hover:text-white"
                >
                    <X size={20} />
                </button>
                
                <h3 className="text-xl font-bold text-white mb-6 text-center">
                    {loginSource === 'qq' ? t('music.qqLoginTitle') : t('music.qrLogin')}
                </h3>

                {loginSource === 'qq' ? (
                    <div className="flex flex-col gap-4">
                        {/* 扫码登录（手机 QQ 扫） */}
                        <div className="flex flex-col items-center gap-2">
                            <div className="bg-white p-2 rounded-xl relative">
                                {qrImg ? (
                                    <img src={qrImg} className="w-40 h-40" alt="QR Code" />
                                ) : (
                                    <div className="w-40 h-40 flex items-center justify-center">
                                        <Loader2 className="animate-spin text-neutral-400" />
                                    </div>
                                )}
                                {qrStatus === 802 && (
                                    <div className="absolute inset-0 bg-white/90 rounded-xl flex flex-col items-center justify-center text-black text-center">
                                        <CheckCircle size={28} className="text-green-500 mb-1" />
                                        <span className="text-xs font-bold">{t('music.qrScanned')}</span>
                                    </div>
                                )}
                            </div>
                            <p className="text-xs text-neutral-400">
                                {qrStatus === 802 ? t('music.qrWaiting') : t('music.qqScanPrompt')}
                            </p>
                        </div>

                        {/* 分隔：或手动 Cookie */}
                        <div className="flex items-center gap-3 text-xs text-neutral-600">
                            <div className="flex-1 h-px bg-white/10" />{t('music.or')}<div className="flex-1 h-px bg-white/10" />
                        </div>

                        <p className="text-xs text-neutral-400 leading-relaxed">
                            {t('music.qqCookieHelp')}
                        </p>
                        <div className="space-y-3">
                            <div>
                                <label className="text-xs font-bold text-neutral-500 mb-1 block">uin (QQ号)</label>
                                <input
                                    type="text"
                                    value={qqCookieUin}
                                    onChange={(e) => setQqCookieUin(e.target.value)}
                                    placeholder="123456789"
                                    className="w-full bg-neutral-800/60 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-green-500/50"
                                />
                            </div>
                            <div>
                                <label className="text-xs font-bold text-neutral-500 mb-1 block">qm_keyst</label>
                                <input
                                    type="password"
                                    value={qqCookieKey}
                                    onChange={(e) => setQqCookieKey(e.target.value)}
                                    placeholder="Q_H_L_..."
                                    className="w-full bg-neutral-800/60 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-green-500/50"
                                />
                            </div>
                        </div>
                        {qqCookieError && <p className="text-xs text-red-400">{qqCookieError}</p>}
                        <button
                            onClick={handleQqCookieLogin}
                            className="w-full py-2.5 rounded-lg bg-green-600 hover:bg-green-500 text-sm font-bold text-white transition-colors"
                        >
                            {t('music.login')}
                        </button>
                    </div>
                ) : (
                <div className="flex flex-col items-center gap-6">
                    {qrStatus === 800 ? (
                        <div className="w-48 h-48 bg-white/5 rounded-xl flex flex-col items-center justify-center gap-2 text-neutral-400">
                            <span className="text-sm">{t('music.qrExpired')}</span>
                            <button onClick={initLogin} className="flex items-center gap-2 text-indigo-400 hover:text-indigo-300 text-sm font-bold">
                                <RefreshCw size={14} /> {t('common.refresh')}
                            </button>
                        </div>
                    ) : (
                        <div className="relative group">
                            <div className="bg-white p-2 rounded-xl">
                                {qrImg ? (
                                    <img src={qrImg} className="w-48 h-48" alt="QR Code" />
                                ) : (
                                    <div className="w-48 h-48 flex items-center justify-center">
                                        <Loader2 className="animate-spin text-neutral-400" />
                                    </div>
                                )}
                            </div>
                            {qrStatus === 802 && (
                                <div className="absolute inset-0 bg-white/90 backdrop-blur-sm rounded-xl flex flex-col items-center justify-center text-black p-4 text-center">
                                    <CheckCircle size={32} className="text-green-500 mb-2" />
                                    <span className="font-bold">{t('music.qrScanned')}</span>
                                    <span className="text-xs text-neutral-600 mt-1">{t('music.confirmOnPhone')}</span>
                                </div>
                            )}
                        </div>
                    )}

                    <div className="text-center space-y-1">
                        <p className="text-sm font-medium text-white">
                            {qrStatus === 801 ? t('music.qrOpenApp') :
                             qrStatus === 802 ? t('music.qrWaiting') :
                             qrStatus === 803 ? t('music.qrLoggingIn') :
                             t('music.qrScanPrompt')}
                        </p>
                        <p className="text-xs text-neutral-500">{t('music.secureLogin')}</p>
                    </div>
                </div>
                )}
             </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Logout Confirm Modal */}
      <AnimatePresence>
          {showLogoutConfirm && (
              <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
                  <motion.div 
                    initial={{ opacity: 0, scale: 0.95 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.95 }}
                    className="bg-neutral-900 border border-white/10 rounded-2xl p-6 w-full max-w-sm shadow-2xl"
                  >
                      <h3 className="text-lg font-bold text-white mb-2">{t('music.confirmLogout')}</h3>
                      <p className="text-sm text-neutral-400 mb-6">{t('music.logoutDesc')}</p>
                      <div className="flex gap-3 justify-end">
                          <button 
                            onClick={() => setShowLogoutConfirm(false)}
                            className="px-4 py-2 text-sm font-medium text-neutral-400 hover:text-white transition-colors"
                          >
                              {t('common.cancel')}
                          </button>
                          <button 
                            onClick={handleLogout}
                            className="px-4 py-2 text-sm font-bold bg-red-500/10 text-red-400 hover:bg-red-500/20 rounded-lg transition-colors flex items-center gap-2"
                          >
                              <LogOut size={16} /> {t('music.logout')}
                          </button>
                      </div>
                  </motion.div>
              </div>
          )}
      </AnimatePresence>

      {/* Top Header */}
      <div className="flex items-center justify-between px-8 pt-8 pb-4 shrink-0">
         <div className="flex gap-6 items-center">
             <h2 className="text-2xl font-bold text-white tracking-tight">{t('music.title')}</h2>
             <div className="flex bg-neutral-900/60 p-1 rounded-xl border border-white/5">
                {[
                    { id: 'discover', label: t('music.tab.discover') },
                    // QQ 无"我的/电台"，仅网易云显示
                    ...(musicSource === 'netease' ? [
                        { id: 'library', label: t('music.tab.library') },
                        { id: 'radio', label: t('music.tab.radio') },
                    ] : []),
                ].map(tab => (
                    <button
                        key={tab.id}
                        onClick={() => { setActiveTab(tab.id as any); setCurrentPlaylist(null); setShowSearchResults(false); }}
                        className={`px-4 py-1.5 text-xs font-bold rounded-lg transition-all ${
                            activeTab === tab.id ? 'bg-indigo-600 text-white shadow-md' : 'text-neutral-400 hover:text-white'
                        }`}
                    >
                        {tab.label}
                    </button>
                ))}
             </div>
         </div>
         <div className="flex items-center gap-2 shrink-0">
             {/* Source Switcher (brand icons) */}
             <div className="flex items-center gap-1.5">
                 {([
                    { src: 'netease', label: t('music.source.netease'), color: '#C20C0C' },
                    { src: 'qq', label: t('music.source.qq'), color: '#2DA44E' },
                 ] as const).map((s) => (
                     <button
                        key={s.src}
                        onClick={() => switchSource(s.src)}
                        title={s.label}
                        className={`w-7 h-7 rounded-lg flex items-center justify-center transition-all ${
                            musicSource === s.src ? 'bg-white/10 scale-105' : 'opacity-40 hover:opacity-80'
                        }`}
                        style={{ color: s.color }}
                     >
                        {s.src === 'netease' ? <NeteaseIcon size={16} /> : <QqMusicIcon size={16} />}
                     </button>
                 ))}
             </div>

             <div className="relative group">
                 <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 text-neutral-500 w-3.5 h-3.5 group-focus-within:text-white transition-colors" />
                 <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => { setSearchQuery(e.target.value); if (!e.target.value.trim()) setShowSearchResults(false); }}
                    onKeyDown={(e) => { if (e.key === 'Enter') doSearch(); }}
                    placeholder={t('music.searchPlaceholder')}
                    className="bg-neutral-900/50 border border-white/5 rounded-full pl-8 pr-3 py-1.5 text-xs text-white focus:outline-none focus:bg-neutral-900 focus:border-indigo-500/50 transition-all w-36 focus:w-44"
                 />
             </div>

             {/* User Profile / Login Button */}
             {musicSource === 'netease' ? (
                 userProfile ? (
                     <div
                        className="flex items-center gap-2 bg-neutral-900/50 pr-3 pl-1 py-1 rounded-full border border-white/5 cursor-pointer hover:bg-neutral-900 transition-colors"
                        onClick={() => setShowLogoutConfirm(true)}
                     >
                         <img src={userProfile.avatarUrl} className="w-6 h-6 rounded-full" />
                         <span className="text-xs font-bold text-white max-w-[80px] truncate">{userProfile.nickname}</span>
                     </div>
                 ) : (
                     <button
                        onClick={initLogin}
                        className="h-8 px-3 rounded-full bg-indigo-600 hover:bg-indigo-500 text-xs font-bold text-white shadow-lg transition-colors flex items-center gap-1.5"
                     >
                        <User size={13} />
                        {t('music.login')}
                     </button>
                 )
             ) : (
                 qqLoggedIn ? (
                     <div
                        className="flex items-center gap-2 bg-neutral-900/50 pr-3 pl-1 py-1 rounded-full border border-white/5 cursor-pointer hover:bg-neutral-900 transition-colors"
                        onClick={handleQqLogout}
                        title={t('music.logout')}
                     >
                         {qqUser?.avatarUrl ? (
                             <img src={qqUser.avatarUrl} className="w-6 h-6 rounded-full" alt="" />
                         ) : (
                             <CheckCircle size={13} className="text-green-400 ml-1" />
                         )}
                         <span className="text-xs font-bold text-white max-w-[80px] truncate">{qqUser?.nickname || t('music.qqLoggedIn')}</span>
                     </div>
                 ) : (
                     <button
                        onClick={initLogin}
                        className="h-8 px-3 rounded-full bg-indigo-600 hover:bg-indigo-500 text-xs font-bold text-white shadow-lg transition-colors flex items-center gap-1.5"
                     >
                        <User size={13} />
                        {t('music.login')}
                     </button>
                 )
             )}
         </div>
      </div>

      {/* Scrollable Content */}
      <div className="flex-1 overflow-y-auto scrollbar-hide p-8 pb-28">
         {showSearchResults ? (
            /* 搜索结果 */
            <div className="space-y-4">
                <h3 className="text-xs font-bold text-neutral-400 uppercase tracking-widest flex items-center gap-2">
                    <List size={14} className="text-indigo-400" /> {t('music.searchResults')}
                </h3>
                {renderSongRows(searchResults)}
            </div>
         ) : currentPlaylist ? (
            /* 歌单/电台详情页 */
            <div className="space-y-6">
                <button onClick={() => setCurrentPlaylist(null)} className="flex items-center gap-1.5 text-sm text-neutral-400 hover:text-white transition-colors">
                    <ArrowRight size={16} className="rotate-180" /> {t('music.back')}
                </button>
                <div className="flex items-end gap-5">
                    <img src={currentPlaylist.cover} className="w-40 h-40 rounded-xl object-cover shadow-xl bg-neutral-800 shrink-0" />
                    <div className="flex-1 min-w-0">
                        <p className="text-xs text-indigo-400 font-bold uppercase tracking-widest">
                            {currentPlaylist.type === 'radio' ? t('music.tab.radio') : t('music.playlist')}
                        </p>
                        <h2 className="text-2xl font-bold text-white mt-1 line-clamp-2">{currentPlaylist.name}</h2>
                        <p className="text-xs text-neutral-500 mt-1">{playlistSongs.length} {t('music.songs')}</p>
                        <button
                            onClick={() => playlistSongs[0] && setCurrentSong(playlistSongs[0])}
                            disabled={playlistSongs.length === 0}
                            className="mt-4 flex items-center gap-2 px-5 py-2 rounded-full bg-indigo-600 hover:bg-indigo-500 text-sm font-bold text-white shadow-lg transition-colors disabled:opacity-40"
                        >
                            <Play size={15} fill="white" className="ml-0.5" /> {t('music.playAll')}
                        </button>
                    </div>
                </div>
                {renderSongRows(playlistSongs)}
            </div>
         ) : activeTab === 'library' && musicSource === 'netease' ? (
            /* 我的 */
            <div className="space-y-4">
                <h3 className="text-xs font-bold text-neutral-400 uppercase tracking-widest flex items-center gap-2">
                    <Heart size={14} className="text-pink-500" /> {t('music.tab.library')}
                </h3>
                {userProfile ? renderPlaylistGrid(userPlaylists, t('music.emptyPlaylist')) : (
                    <div className="text-center py-12 text-neutral-500 text-sm border border-white/5 rounded-xl">{t('music.loginForPlaylists')}</div>
                )}
            </div>
         ) : activeTab === 'radio' && musicSource === 'netease' ? (
            /* 电台 */
            <div className="space-y-4">
                <h3 className="text-xs font-bold text-neutral-400 uppercase tracking-widest flex items-center gap-2">
                    <Disc size={14} className="text-indigo-400" /> {t('music.tab.radio')}
                </h3>
                {renderPlaylistGrid(radios, t('music.loadingPlaylist'))}
            </div>
         ) : (
            /* 发现：推荐歌单（网易云登录时首格为"每日推荐"） */
            <div className="space-y-4">
                <h3 className="text-xs font-bold text-neutral-400 uppercase tracking-widest flex items-center gap-2">
                    <Disc size={14} className="text-indigo-400" /> {t('music.recommendPlaylist')}
                </h3>
                {renderPlaylistGrid(
                    musicSource === 'netease'
                        ? [
                            ...(userProfile && dailySongs.length > 0
                                ? [{ id: 'daily', name: t('music.dailyRecommend'), cover: dailySongs[0]?.cover || '', trackCount: dailySongs.length, source: 'netease' as const, type: 'playlist' as const }]
                                : []),
                            ...(userProfile && recommendedPlaylists.length > 0 ? recommendedPlaylists : discoverPlaylists),
                          ]
                        : discoverPlaylists,
                    t('music.loadingRecommend'),
                )}
            </div>
         )}
      </div>

      {/* Playlist Queue Overlay */}
      <AnimatePresence>
        {showQueue && (
            <motion.div
                initial={{ y: '100%', opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                exit={{ y: '100%', opacity: 0 }}
                transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                className="absolute right-0 bottom-24 top-0 w-80 bg-neutral-900/95 backdrop-blur-2xl border-l border-white/10 z-40 flex flex-col shadow-2xl"
            >
                <div className="flex items-center justify-between p-4 border-b border-white/5">
                    <h3 className="text-sm font-bold text-white">{t('music.queue')}</h3>
                    <button 
                        onClick={() => setShowQueue(false)}
                        className="p-1.5 hover:bg-white/10 rounded-lg text-neutral-400 hover:text-white"
                    >
                        <X size={16} />
                    </button>
                </div>
                <div className="flex-1 overflow-y-auto p-2">
                    {/* Queue items */}
                    {displaySongs.map((song, i) => (
                        <div key={i} className={`flex items-center gap-3 p-2 rounded-lg hover:bg-white/5 cursor-pointer ${currentSong?.id === song.id ? 'bg-white/5' : ''}`}>
                             <div className="relative w-8 h-8 rounded overflow-hidden shrink-0">
                                 <img src={song.cover} className="w-full h-full object-cover" />
                                 {currentSong?.id === song.id && (
                                     <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
                                         <div className="w-1 h-3 bg-indigo-500 rounded-full animate-pulse mx-[1px]"/>
                                         <div className="w-1 h-4 bg-indigo-500 rounded-full animate-pulse mx-[1px] delay-75"/>
                                         <div className="w-1 h-2 bg-indigo-500 rounded-full animate-pulse mx-[1px] delay-150"/>
                                     </div>
                                 )}
                             </div>
                             <div className="min-w-0">
                                 <p className={`text-xs font-medium truncate ${currentSong?.id === song.id ? 'text-indigo-300' : 'text-white'}`}>{song.title}</p>
                                 <p className="text-[10px] text-neutral-500 truncate">{song.artist}</p>
                             </div>
                        </div>
                    ))}
                    {displaySongs.length === 0 && (
                        <div className="p-4 text-center text-xs text-neutral-500">
                            {t('music.queueEmpty')}
                        </div>
                    )}
                </div>
            </motion.div>
        )}
      </AnimatePresence>

      {/* 不可播放提示 */}
      <AnimatePresence>
        {playbackNotice && (
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 12 }}
            className="absolute bottom-28 left-1/2 -translate-x-1/2 z-[60] flex items-center gap-2 bg-amber-500/15 border border-amber-500/30 text-amber-300 text-xs font-medium px-4 py-2 rounded-full backdrop-blur-md shadow-lg"
          >
            <RefreshCw size={13} className="animate-spin" style={{ animationDuration: '2s' }} />
            {playbackNotice}
          </motion.div>
        )}
      </AnimatePresence>

      {/*
         --- BOTTOM PLAYER BAR ---
      */}
      {currentSong && (
      <div 
        className="absolute bottom-0 left-0 right-0 h-24 bg-[#0f0f0f]/95 backdrop-blur-xl border-t border-white/5 flex items-center justify-between px-6 z-50"
      >
          {/* Audio Element */}
          <audio 
            ref={audioRef} 
            onTimeUpdate={handleTimeUpdate} 
            onEnded={handleEnded} 
            onError={(e) => console.error("Audio error", e)}
          />

          {/* LEFT: Album Art & Info */}
          <div className="flex items-center gap-4 w-[25%]">
              <motion.div 
                layoutId="mini-cover"
                className="w-14 h-14 rounded-xl overflow-hidden cursor-pointer shadow-lg relative group shrink-0"
                onClick={() => setImmersiveMode(true)}
                whileHover={{ scale: 1.05 }}
              >
                 <img src={currentSong.cover} className="w-full h-full object-cover" />
                 <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                     <ArrowRight size={20} className="text-white -rotate-45" />
                 </div>
              </motion.div>
              <div className="min-w-0">
                  <div className="flex items-center gap-1.5">
                      <h4 className="text-sm font-bold text-white truncate cursor-pointer hover:underline" title={currentSong.title}>{currentSong.title}</h4>
                      {isTrial && (
                          <span className="shrink-0 px-1.5 py-0.5 rounded text-[10px] font-semibold bg-amber-500/20 text-amber-400 border border-amber-500/30" title={t('music.trialTip')}>
                              {t('music.trial')}
                          </span>
                      )}
                  </div>
                  <p className="text-xs text-neutral-400 truncate cursor-pointer hover:text-neutral-300" title={currentSong.artist}>{currentSong.artist}</p>
              </div>
          </div>

          {/* CENTER: Controls & Progress */}
          <div className="flex flex-col items-center justify-center gap-2 flex-1 max-w-[40%]">
              {/* Buttons */}
              <div className="flex items-center gap-6">
                  <button onClick={playPrev} className="text-neutral-400 hover:text-white transition-colors">
                      <SkipBack size={20} fill="currentColor"/>
                  </button>
                  <button 
                    onClick={() => setIsPlaying(!isPlaying)}
                    className="w-10 h-10 rounded-full bg-white text-black flex items-center justify-center hover:scale-105 transition-transform shadow-[0_0_15px_rgba(255,255,255,0.2)]"
                  >
                      {isPlaying ? <Pause size={18} fill="currentColor"/> : <Play size={18} fill="currentColor" className="ml-0.5"/>}
                  </button>
                  <button onClick={playNext} className="text-neutral-400 hover:text-white transition-colors">
                      <SkipForward size={20} fill="currentColor"/>
                  </button>
              </div>
              
              {/* Progress Bar (Integrated) */}
              <div className="w-full flex items-center gap-3">
                  <span className="text-[10px] text-neutral-500 font-mono w-8 text-right">{formatDuration(currentTime * 1000)}</span>
                  <div className="flex-1 h-1 bg-neutral-800 rounded-full cursor-pointer relative group flex items-center">
                      <div 
                        className="absolute inset-y-0 left-0 bg-indigo-500 rounded-full group-hover:bg-indigo-400 transition-colors pointer-events-none z-10" 
                        style={{ width: `${(currentTime / (duration || 1)) * 100}%` }}
                      >
                          <div className="absolute right-0 top-1/2 -translate-y-1/2 w-2 h-2 bg-white rounded-full shadow opacity-0 group-hover:opacity-100 transition-opacity"/>
                      </div>
                      <input 
                        type="range" 
                        min="0" 
                        max={duration || 0} 
                        value={currentTime} 
                        onChange={handleSeek}
                        className="absolute inset-0 w-full opacity-0 cursor-pointer z-20"
                      />
                  </div>
                  <span className="text-[10px] text-neutral-500 font-mono w-8">{formatDuration((duration || 0) * 1000)}</span>
              </div>
          </div>

          {/* RIGHT: Volume & Tools */}
          <div className="flex items-center justify-end gap-4 w-[25%]">
              <div className="flex items-center gap-2 group">
                  <Volume2 size={18} className="text-neutral-400 group-hover:text-white transition-colors" />
                  <div className="w-20 h-1 bg-neutral-800 rounded-full cursor-pointer overflow-hidden relative">
                      <div className="h-full bg-neutral-400 group-hover:bg-white transition-colors" style={{ width: `${volume}%` }} />
                      <input 
                        type="range" min="0" max="100" 
                        value={volume} onChange={(e) => setVolume(Number(e.target.value))}
                        className="absolute inset-0 opacity-0 cursor-pointer"
                      />
                  </div>
              </div>
              <div className="h-6 w-px bg-white/10 mx-1" />
              <button 
                onClick={() => setShowQueue(!showQueue)}
                className={`p-2 rounded-lg hover:bg-white/10 transition-colors relative ${showQueue ? 'text-indigo-400 bg-white/10' : 'text-neutral-400 hover:text-white'}`}
              >
                  <List size={20} />
                  {/* Indicator for "Playing" state logic could go here */}
              </button>
          </div>
      </div>
      )}
      {/* Immersive Mode Overlay */}
      <AnimatePresence>
          {immersiveMode && currentSong && (
              <motion.div 
                  initial={{ opacity: 0, y: 50 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: 50 }}
                  transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
                  className="fixed inset-0 z-[200] bg-[#0a0a0a] flex flex-col overflow-hidden"
              >
                  {/* Background Blur */}
                  <div className="absolute inset-0 z-0">
                      <img src={currentSong.cover} className="w-full h-full object-cover opacity-30 blur-[100px] scale-125" />
                      <div className="absolute inset-0 bg-black/40" />
                  </div>

                  {/* Header */}
                  <div className="relative z-10 flex items-center justify-between px-10 py-8">
                      <button onClick={() => setImmersiveMode(false)} className="p-2 rounded-full bg-white/5 hover:bg-white/10 text-white transition-colors">
                          <X size={24} />
                      </button>
                      <div className="flex flex-col items-center">
                          <span className="text-xs font-bold text-white/50 tracking-widest uppercase">Now Playing</span>
                          <span className="text-sm font-bold text-white">{currentSong.title}</span>
                      </div>
                      <button className="p-2 rounded-full hover:bg-white/10 text-white transition-colors">
                          <MoreHorizontal size={24} />
                      </button>
                  </div>

                  {/* Content */}
                  <div className="relative z-10 flex-1 flex gap-20 px-20 pb-10 overflow-hidden">
                      {/* Left: Cover & Controls */}
                    <div className="w-1/3 flex flex-col justify-center gap-4">
                        <div className="aspect-square w-full max-w-[200px] mx-auto rounded-3xl overflow-hidden shadow-2xl ring-1 ring-white/10 relative group shrink-0">
                            <img src={currentSong.cover} className="w-full h-full object-cover" />
                            <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-60" />
                        </div>
                        
                        <div className="space-y-2 w-full overflow-hidden text-center">
                            <ScrollingText text={currentSong.title} className="text-3xl font-bold text-white leading-tight" />
                            <p className="text-lg text-neutral-400 font-medium truncate">{currentSong.artist}</p>
                        </div>

                      {/* Progress Bar */}
                           <div className="space-y-2">
                              <div className="flex items-center justify-between text-xs font-mono text-neutral-400">
                                  <span>{formatDuration(currentTime * 1000)}</span>
                                  <span>{formatDuration((duration || 0) * 1000)}</span>
                              </div>
                              <div className="h-1.5 bg-white/10 rounded-full cursor-pointer relative group" onClick={(e) => {
                                  if (audioRef.current) {
                                      const rect = e.currentTarget.getBoundingClientRect();
                                      const p = (e.clientX - rect.left) / rect.width;
                                      const t = p * (duration || 0);
                                      audioRef.current.currentTime = t;
                                      setCurrentTime(t);
                                  }
                              }}>
                                  <div className="absolute inset-y-0 left-0 bg-white rounded-full" style={{ width: `${(currentTime / (duration || 1)) * 100}%` }}>
                                      <div className="absolute right-0 top-1/2 -translate-y-1/2 w-3 h-3 bg-white rounded-full shadow-lg scale-0 group-hover:scale-100 transition-transform" />
                                  </div>
                              </div>
                          </div>

                          {/* Main Controls */}
                          <div className="flex items-center justify-center px-4">
                              <div className="flex items-center gap-8">
                                  <button onClick={playPrev} className="text-white hover:text-indigo-400 transition-colors"><SkipBack size={32} fill="currentColor" /></button>
                                  <button onClick={() => setIsPlaying(!isPlaying)} className="w-20 h-20 rounded-full bg-white text-black flex items-center justify-center hover:scale-105 transition-transform shadow-2xl">
                                      {isPlaying ? <Pause size={32} fill="currentColor" /> : <Play size={32} fill="currentColor" className="ml-1" />}
                                  </button>
                                  <button onClick={playNext} className="text-white hover:text-indigo-400 transition-colors"><SkipForward size={32} fill="currentColor" /></button>
                              </div>
                          </div>
                      </div>

                      {/* Right: Lyrics */}
                      <div className="flex-1 relative h-full overflow-hidden mask-image-gradient-vertical">
                          <div 
                              ref={lyricContainerRef}
                              className="absolute inset-0 overflow-y-auto scrollbar-hide py-[40vh] space-y-8 pr-20"
                              style={{ maskImage: 'linear-gradient(to bottom, transparent, black 10%, black 90%, transparent)' }}
                          >
                              {parsedLyrics.length > 0 ? parsedLyrics.map((line, i) => (
                                  <div 
                                      key={i} 
                                      className={`transition-all duration-500 ease-out cursor-pointer origin-left ${
                                          i === currentLineIndex 
                                          ? 'opacity-100 scale-100 blur-0' 
                                          : 'opacity-50 scale-95 blur-[1px]'
                                      }`}
                                      onClick={() => {
                                          if (audioRef.current) {
                                              audioRef.current.currentTime = line.startTime / 1000;
                                              setCurrentTime(line.startTime / 1000);
                                          }
                                      }}
                                  >
                                      <p className={`text-3xl font-bold leading-normal ${i === currentLineIndex ? 'text-white' : 'text-neutral-300'}`}>
                                          {line.words ? (
                                              line.words.map((word, wIdx) => {
                                                  // If not current line, show default style
                                                  if (i !== currentLineIndex) {
                                                      return (
                                                          <span key={wIdx} className="inline-block mr-1 transition-all duration-700 text-neutral-300 opacity-60 blur-[0.5px]">
                                                              {word.text}
                                                          </span>
                                                      );
                                                  }

                                                  const wStart = word.startTime / 1000;
                                                  const wEnd = (word.startTime + word.duration) / 1000;
                                                  const isPast = currentTime >= wEnd;
                                                  const isCurrent = currentTime >= wStart && !isPast;
                                                  
                                                  // Dynamic duration logic:
                                                  // 1. Current (Lighting up): Duration = word duration (linear sync)
                                                  // 2. Past (Floating up): Duration = word duration (match lighting speed)
                                                  // 3. Future (Reset): Duration = 500ms
                                                  const transitionDuration = isCurrent 
                                                      ? `${word.duration}ms` 
                                                      : (isPast ? `${word.duration}ms` : '500ms');

                                                  let wordClass = "transition-all ease-out inline-block mr-1 ";
                                                  
                                                  if (isPast) {
                                                      // Past: Float up, keep glow, stay there
                                                      wordClass += "text-white -translate-y-[1px] opacity-100 blur-0 drop-shadow-[0_0_5px_rgba(255,255,255,0.4)]";
                                                  } else if (isCurrent) {
                                                      // Current: Glow AND Float up (start animating to -1px immediately)
                                                      wordClass += "text-white -translate-y-[1px] opacity-100 blur-0 drop-shadow-[0_0_10px_rgba(255,255,255,0.6)]";
                                                  } else {
                                                      // Future: Dim, no float
                                                      wordClass += "text-neutral-300 translate-y-0 opacity-60 blur-[0.5px]";
                                                  }
                                                  
                                                  return (
                                                      <span 
                                                          key={wIdx} 
                                                          className={wordClass}
                                                          style={{ transitionDuration }}
                                                      >
                                                          {word.text}
                                                      </span>
                                                  )
                                              })
                                          ) : (
                                              line.text
                                          )}
                                      </p>
                                      {line.translation && (
                                          <p className={`text-lg mt-2 font-light transition-opacity duration-500 ${i === currentLineIndex ? 'text-white/80' : 'text-white/40'}`}>{line.translation}</p>
                                      )}
                                  </div>
                              )) : (
                                  <div className="h-full flex items-center justify-center text-neutral-500 text-lg">
                                      {t('music.noLyrics')}
                                  </div>
                              )}
                          </div>
                      </div>
                  </div>
              </motion.div>
          )}
      </AnimatePresence>
    </div>
  );
};
