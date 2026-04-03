import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Search, Play, Pause, SkipForward, SkipBack, Heart, Disc, ArrowRight, List, Volume2, PlusCircle, MoreHorizontal, X, User, Loader2, RefreshCw, CheckCircle, LogOut, Shuffle, Repeat } from 'lucide-react';
import { Song, Playlist, NeteaseUserProfile, NeteaseUserPlaylistResponse } from '../types';
import { api, setCookie, getCookie, clearCookie } from '../services/netease';

interface MusicPlayerProps {
    immersiveMode: boolean;
    setImmersiveMode: (enabled: boolean) => void;
}

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
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentSong, setCurrentSong] = useState<Song | null>(null);
  const [activeTab, setActiveTab] = useState<'discover' | 'library' | 'radio'>('discover');
  const [volume, setVolume] = useState(75);
  const [showQueue, setShowQueue] = useState(false);

  // --- Netease Login State ---
  const [userProfile, setUserProfile] = useState<NeteaseUserProfile | null>(null);
  const [showLogin, setShowLogin] = useState(false);
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [qrKey, setQrKey] = useState('');
  const [qrImg, setQrImg] = useState('');
  const [qrStatus, setQrStatus] = useState(0); // 800:expired, 801:waiting, 802:scanned, 803:success
  const loginCheckInterval = useRef<NodeJS.Timeout | null>(null);

  // --- Daily Recommend State ---
  const [dailySongs, setDailySongs] = useState<Song[]>([]);
  const [recommendedPlaylists, setRecommendedPlaylists] = useState<Playlist[]>([]);
  const [userPlaylists, setUserPlaylists] = useState<Playlist[]>([]);

  // --- Playlist State ---
  const [currentPlaylist, setCurrentPlaylist] = useState<{ id: string, name: string } | null>(null);
  const [playlistSongs, setPlaylistSongs] = useState<Song[]>([]);
  const [isLoadingPlaylist, setIsLoadingPlaylist] = useState(false);

  // --- Audio State ---
  const audioRef = useRef<HTMLAudioElement>(null);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [lyric, setLyric] = useState<string>('');
  const [parsedLyrics, setParsedLyrics] = useState<LyricLine[]>([]);
  const [currentLineIndex, setCurrentLineIndex] = useState(0);
  const lyricContainerRef = useRef<HTMLDivElement>(null);

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


  // Check login status on mount
  useEffect(() => {
    const checkLogin = async () => {
      const cookie = getCookie();
      if (cookie) {
        try {
          const res = await api.getLoginStatus();
          if (res.data?.profile) {
            setUserProfile(res.data.profile);
            // If logged in, fetch daily recommend
            fetchDailySongs();
            fetchRecommendedPlaylists();
            fetchUserPlaylists(res.data.profile.userId);
          } else {
             // Cookie might be invalid
             // clearCookie(); 
          }
        } catch (e) {
          console.error("Failed to check login status", e);
        }
      }
    };
    checkLogin();
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

  const fetchPlaylistSongs = async (id: string) => {
      setIsLoadingPlaylist(true);
      setPlaylistSongs([]);
      try {
          const res = await api.getPlaylistTracks(id);
          if (res.code === 200) {
              const songs: Song[] = res.songs.map(s => ({
                  id: s.id.toString(),
                  title: s.name,
                  artist: s.ar.map(a => a.name).join('/'),
                  cover: s.al.picUrl,
                  duration: formatDuration(s.dt)
              }));
              setPlaylistSongs(songs);
          }
      } catch (e) {
          console.error("Failed to fetch playlist songs", e);
      } finally {
          setIsLoadingPlaylist(false);
      }
  };

  const handlePlaylistClick = (playlist: Playlist) => {
      setCurrentPlaylist({ id: playlist.id, name: playlist.name });
      fetchPlaylistSongs(playlist.id);
  };

  const formatDuration = (ms: number) => {
      const totalSeconds = Math.floor(ms / 1000);
      const minutes = Math.floor(totalSeconds / 60);
      const seconds = totalSeconds % 60;
      return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  };

  // Handle Login Flow
  const initLogin = async () => {
    try {
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

  // Poll QR Status
  useEffect(() => {
    if (showLogin && qrKey && !userProfile) {
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
  }, [showLogin, qrKey, userProfile, qrStatus]);

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

        const fetchData = async () => {
            try {
                // Fetch URL
                const res = await api.getSongUrl(currentSong.id);
                if (res.code === 200 && res.data[0]?.url) {
                    if (audioRef.current) {
                        audioRef.current.src = res.data[0].url;
                        // Use a small timeout to ensure DOM is ready and prevent race conditions
                        setTimeout(async () => {
                            try {
                                await audioRef.current?.play();
                                setIsPlaying(true);
                            } catch (e) {
                                console.error("Play failed", e);
                            }
                        }, 100);
                    }
                } else {
                    console.warn("No URL found for song", currentSong.title);
                }

                // Fetch Lyric
                const lrcRes = await api.getLyric(currentSong.id);
                if (lrcRes.code === 200) {
                    setLyric(lrcRes.lrc?.lyric || '');
                    parseLyrics(lrcRes.yrc?.lyric, lrcRes.lrc?.lyric, lrcRes.tlyric?.lyric);
                } else {
                    setLyric('');
                    setParsedLyrics([]);
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
      setUserProfile(null);
      setDailySongs([]);
      setRecommendedPlaylists([]);
      setUserPlaylists([]);
      setShowLogoutConfirm(false);
  };

  // Display songs: either playlist songs (if selected), daily songs (if logged in and fetched) or empty
  const displaySongs = currentPlaylist ? playlistSongs : dailySongs;
  const displayPlaylists = (userProfile && recommendedPlaylists.length > 0) ? recommendedPlaylists : [];

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
                
                <h3 className="text-xl font-bold text-white mb-6 text-center">扫码登录</h3>
                
                <div className="flex flex-col items-center gap-6">
                    {qrStatus === 800 ? (
                        <div className="w-48 h-48 bg-white/5 rounded-xl flex flex-col items-center justify-center gap-2 text-neutral-400">
                            <span className="text-sm">二维码已过期</span>
                            <button onClick={initLogin} className="flex items-center gap-2 text-indigo-400 hover:text-indigo-300 text-sm font-bold">
                                <RefreshCw size={14} /> 刷新
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
                                    <span className="font-bold">扫码成功!</span>
                                    <span className="text-xs text-neutral-600 mt-1">请在手机上确认</span>
                                </div>
                            )}
                        </div>
                    )}

                    <div className="text-center space-y-1">
                        <p className="text-sm font-medium text-white">
                            {qrStatus === 801 ? '请打开网易云音乐APP' : 
                             qrStatus === 802 ? '等待确认...' : 
                             qrStatus === 803 ? '登录中...' : 
                             '扫码登录网易云音乐'}
                        </p>
                        <p className="text-xs text-neutral-500">安全登录</p>
                    </div>
                </div>
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
                      <h3 className="text-lg font-bold text-white mb-2">确认退出登录?</h3>
                      <p className="text-sm text-neutral-400 mb-6">退出后将无法访问您的私人歌单和日推。</p>
                      <div className="flex gap-3 justify-end">
                          <button 
                            onClick={() => setShowLogoutConfirm(false)}
                            className="px-4 py-2 text-sm font-medium text-neutral-400 hover:text-white transition-colors"
                          >
                              取消
                          </button>
                          <button 
                            onClick={handleLogout}
                            className="px-4 py-2 text-sm font-bold bg-red-500/10 text-red-400 hover:bg-red-500/20 rounded-lg transition-colors flex items-center gap-2"
                          >
                              <LogOut size={16} /> 退出
                          </button>
                      </div>
                  </motion.div>
              </div>
          )}
      </AnimatePresence>

      {/* Top Header */}
      <div className="flex items-center justify-between px-8 pt-8 pb-4 shrink-0">
         <div className="flex gap-6 items-center">
             <h2 className="text-2xl font-bold text-white tracking-tight">音乐</h2>
             <div className="flex bg-neutral-900/60 p-1 rounded-xl border border-white/5">
                {[
                    { id: 'discover', label: '发现' },
                    { id: 'library', label: '我的' },
                    { id: 'radio', label: '电台' }
                ].map(tab => (
                    <button 
                        key={tab.id}
                        onClick={() => setActiveTab(tab.id as any)}
                        className={`px-4 py-1.5 text-xs font-bold rounded-lg transition-all ${
                            activeTab === tab.id ? 'bg-indigo-600 text-white shadow-md' : 'text-neutral-400 hover:text-white'
                        }`}
                    >
                        {tab.label}
                    </button>
                ))}
             </div>
         </div>
         <div className="flex items-center gap-4">
             <div className="relative group">
                 <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-500 w-4 h-4 group-focus-within:text-white transition-colors" />
                 <input 
                    type="text" 
                    placeholder="搜索..." 
                    className="bg-neutral-900/50 border border-white/5 rounded-full pl-9 pr-4 py-2 text-sm text-white focus:outline-none focus:bg-neutral-900 focus:border-indigo-500/50 transition-all w-48"
                 />
             </div>
             
             {/* User Profile / Login Button */}
             {userProfile ? (
                 <div 
                    className="flex items-center gap-3 bg-neutral-900/50 pr-4 pl-1 py-1 rounded-full border border-white/5 cursor-pointer hover:bg-neutral-900 transition-colors group relative" 
                    onClick={() => setShowLogoutConfirm(true)}
                 >
                     <img src={userProfile.avatarUrl} className="w-8 h-8 rounded-full" />
                     <span className="text-xs font-bold text-white max-w-[100px] truncate">{userProfile.nickname}</span>
                 </div>
             ) : (
                 <button 
                    onClick={initLogin}
                    className="h-9 px-4 rounded-full bg-indigo-600 hover:bg-indigo-500 text-xs font-bold text-white shadow-lg transition-colors flex items-center gap-2"
                 >
                    <User size={14} />
                    登录
                 </button>
             )}
         </div>
      </div>

      {/* Scrollable Content */}
      <div className="flex-1 overflow-y-auto scrollbar-hide p-8 space-y-10 pb-28">
         {/* Featured Section */}
         <section>
            <div className="flex justify-between items-center mb-4">
                <h3 className="text-xs font-bold text-neutral-400 uppercase tracking-widest flex items-center gap-2">
                    <Disc size={14} className="text-indigo-400"/> {userProfile ? '每日推荐歌单' : '推荐歌单'}
                </h3>
            </div>
            <div className="grid grid-cols-3 gap-4">
                {displayPlaylists.map(p => (
                    <motion.div 
                        key={p.id}
                        whileHover={{ y: -5 }}
                        className="relative aspect-video rounded-xl overflow-hidden group cursor-pointer shadow-lg"
                    >
                        <img src={p.cover} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700" />
                        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent flex flex-col justify-end p-4">
                            <span className="text-sm font-bold text-white line-clamp-1">{p.name}</span>
                            <span className="text-xs text-neutral-300 opacity-0 group-hover:opacity-100 transition-opacity translate-y-2 group-hover:translate-y-0 duration-300">每日推荐</span>
                        </div>
                        <div className="absolute top-3 right-3 w-8 h-8 bg-white/20 backdrop-blur-md rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                             <Play size={12} fill="white" className="ml-0.5 text-white" />
                        </div>
                    </motion.div>
                ))}
                {displayPlaylists.length === 0 && (
                     <div className="col-span-3 text-center py-10 text-neutral-500 text-sm border border-white/5 rounded-xl">
                        {userProfile ? '加载推荐歌单中...' : '登录查看每日推荐歌单'}
                     </div>
                )}
            </div>
         </section>

         {/* Your Tracks Section */}
         <section>
            <div className="flex justify-between items-center mb-4">
                <h3 className="text-xs font-bold text-neutral-400 uppercase tracking-widest flex items-center gap-2">
                    <Heart size={14} className="text-pink-500"/> 你的收藏
                </h3>
                <button className="text-xs text-indigo-400 hover:text-indigo-300 font-medium">查看全部</button>
            </div>
            <div className="flex gap-4 overflow-x-auto pb-4 scrollbar-hide">
                <motion.div className="w-32 shrink-0 aspect-square rounded-xl border-2 border-dashed border-white/10 flex flex-col items-center justify-center gap-2 text-neutral-500 hover:border-white/30 hover:text-white transition-colors cursor-pointer bg-white/5">
                    <PlusCircle size={24} />
                    <span className="text-xs font-medium">新建歌单</span>
                </motion.div>
                {userPlaylists.map(p => (
                    <div key={p.id} className="w-32 shrink-0 group cursor-pointer" onClick={() => handlePlaylistClick(p)}>
                        <div className="aspect-square rounded-xl overflow-hidden mb-2 relative shadow-md">
                            <img src={p.cover} className="w-full h-full object-cover" />
                            <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                                <Play size={20} fill="white" className="text-white"/>
                            </div>
                        </div>
                        <p className="text-xs font-semibold text-neutral-200 truncate group-hover:text-white transition-colors">{p.name}</p>
                        <p className="text-[10px] text-neutral-500">{p.trackCount} 首歌曲</p>
                    </div>
                ))}
                {!userProfile && (
                     <div className="flex items-center justify-center w-32 h-32 text-neutral-600 text-xs text-center border border-white/5 rounded-xl">
                        登录查看<br/>收藏歌单
                     </div>
                )}
            </div>
         </section>

         {/* Recent List / Daily Recommend List / Playlist Songs */}
         <section>
            <h3 className="text-xs font-bold text-neutral-400 mb-3 uppercase tracking-widest flex items-center gap-2">
                <List size={14} className="text-indigo-400"/> 
                {currentPlaylist ? (
                    <span className="flex items-center gap-2 cursor-pointer hover:text-white transition-colors" onClick={() => setCurrentPlaylist(null)}>
                        <ArrowRight size={14} className="rotate-180" />
                        {currentPlaylist.name}
                    </span>
                ) : (
                    userProfile ? '每日推荐' : '最近播放'
                )}
            </h3>
            <div className="space-y-1">
                {displaySongs.map((song, i) => (
                    <div 
                        key={song.id}
                        onClick={() => setCurrentSong(song)}
                        className={`flex items-center gap-4 p-3 rounded-xl transition-all cursor-pointer group ${
                            currentSong?.id === song.id 
                            ? 'bg-gradient-to-r from-indigo-500/20 to-transparent border border-indigo-500/20' 
                            : 'hover:bg-white/5 border border-transparent'
                        }`}
                    >
                        <span className="w-6 text-center text-xs text-neutral-500 font-mono group-hover:text-white">{i + 1}</span>
                        <img src={song.cover} className="w-10 h-10 rounded-lg object-cover shadow-sm" />
                        <div className="flex-1 min-w-0">
                            <h4 className={`text-sm font-medium truncate ${currentSong?.id === song.id ? 'text-indigo-300' : 'text-white'}`}>{song.title}</h4>
                            <p className="text-xs text-neutral-500 truncate group-hover:text-neutral-400">{song.artist}</p>
                        </div>
                        <span className="text-xs text-neutral-600 font-mono group-hover:text-neutral-500">{song.duration}</span>
                        <div className="flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                             <button className="p-1.5 hover:bg-white/10 rounded-full text-neutral-400 hover:text-white">
                                <Heart size={14} />
                             </button>
                             <button className="p-1.5 hover:bg-white/10 rounded-full text-neutral-400 hover:text-white">
                                <MoreHorizontal size={14} />
                             </button>
                        </div>
                    </div>
                ))}
                {displaySongs.length === 0 && (
                    <div className="text-center py-10 text-neutral-500 text-sm border border-white/5 rounded-xl">
                        {isLoadingPlaylist ? (
                             <span className="flex items-center justify-center gap-2"><Loader2 className="animate-spin" size={16}/> 加载歌单中...</span>
                        ) : (
                             userProfile ? (currentPlaylist ? '歌单为空' : '加载每日推荐中...') : '登录查看每日推荐歌曲'
                        )}
                    </div>
                )}
            </div>
         </section>
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
                    <h3 className="text-sm font-bold text-white">播放队列</h3>
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
                            队列为空
                        </div>
                    )}
                </div>
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
                  <h4 className="text-sm font-bold text-white truncate cursor-pointer hover:underline" title={currentSong.title}>{currentSong.title}</h4>
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
                                      暂无歌词
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
