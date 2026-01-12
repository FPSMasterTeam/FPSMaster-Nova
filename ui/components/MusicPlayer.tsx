import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Search, Play, Pause, SkipForward, SkipBack, Heart, Disc, ArrowRight, List, Volume2, PlusCircle, MoreHorizontal, X } from 'lucide-react';
import { Song, Playlist } from '../types';

// Mock Data
const MOCK_SONGS: Song[] = [
  { id: '1', title: 'Midnight City', artist: 'M83', cover: 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=200&auto=format&fit=crop', duration: '4:03', lyrics: ['Waiting in a car'] },
  { id: '2', title: 'Starboy', artist: 'The Weeknd', cover: 'https://images.unsplash.com/photo-1619983081563-430f63602796?q=80&w=200&auto=format&fit=crop', duration: '3:50', lyrics: ["I'm tryna put you in the worst mood, ah"] },
  { id: '3', title: 'Neon Lights', artist: 'Kraftwerk', cover: 'https://images.unsplash.com/photo-1493225255756-d9584f8606e9?q=80&w=200&auto=format&fit=crop', duration: '5:21' },
];

const RECOMMENDED: Playlist[] = [
  { id: 'p1', name: '每日推荐', cover: 'https://images.unsplash.com/photo-1494232410401-ad00d5433cfa?q=80&w=200&auto=format&fit=crop' },
  { id: 'p2', name: '私人FM', cover: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=200&auto=format&fit=crop' },
  { id: 'p3', name: '助眠', cover: 'https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?q=80&w=200&auto=format&fit=crop' }, // Replaced with a reliable night/moody image
];

const YOUR_PLAYLISTS: Playlist[] = [
    { id: 'yp1', name: 'Coding Focus', cover: 'https://images.unsplash.com/photo-1550745165-9bc0b252726f?q=80&w=200&auto=format&fit=crop' },
    { id: 'yp2', name: 'Late Night', cover: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=200&auto=format&fit=crop' },
    { id: 'yp3', name: 'Favorites', cover: 'https://images.unsplash.com/photo-1493225255756-d9584f8606e9?q=80&w=200&auto=format&fit=crop' },
];

interface MusicPlayerProps {
    setImmersiveMode: (enabled: boolean) => void;
}

export const MusicPlayer: React.FC<MusicPlayerProps> = ({ setImmersiveMode }) => {
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentSong, setCurrentSong] = useState<Song>(MOCK_SONGS[0]);
  const [activeTab, setActiveTab] = useState<'discover' | 'library' | 'radio'>('discover');
  const [volume, setVolume] = useState(75);
  const [showQueue, setShowQueue] = useState(false);

  return (
    <div className="relative h-full flex flex-col bg-black/20 overflow-hidden">
      
      {/* Top Header */}
      <div className="flex items-center justify-between px-8 pt-8 pb-4 shrink-0">
         <div className="flex gap-6 items-center">
             <h2 className="text-2xl font-bold text-white tracking-tight">Music</h2>
             <div className="flex bg-neutral-900/60 p-1 rounded-xl border border-white/5">
                {['discover', 'library', 'radio'].map(tab => (
                    <button 
                        key={tab}
                        onClick={() => setActiveTab(tab as any)}
                        className={`px-4 py-1.5 text-xs font-bold rounded-lg transition-all capitalize ${
                            activeTab === tab ? 'bg-indigo-600 text-white shadow-md' : 'text-neutral-400 hover:text-white'
                        }`}
                    >
                        {tab}
                    </button>
                ))}
             </div>
         </div>
         <div className="flex items-center gap-4">
             <div className="relative group">
                 <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-500 w-4 h-4 group-focus-within:text-white transition-colors" />
                 <input 
                    type="text" 
                    placeholder="Search..." 
                    className="bg-neutral-900/50 border border-white/5 rounded-full pl-9 pr-4 py-2 text-sm text-white focus:outline-none focus:bg-neutral-900 focus:border-indigo-500/50 transition-all w-48"
                 />
             </div>
             <div className="w-9 h-9 rounded-full bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-xs font-bold text-white shadow-lg ring-2 ring-white/10">
                 U
             </div>
         </div>
      </div>

      {/* Scrollable Content */}
      <div className="flex-1 overflow-y-auto scrollbar-hide p-8 space-y-10 pb-28">
         {/* Featured Section */}
         <section>
            <div className="flex justify-between items-center mb-4">
                <h3 className="text-xs font-bold text-neutral-400 uppercase tracking-widest flex items-center gap-2">
                    <Disc size={14} className="text-indigo-400"/> For You
                </h3>
            </div>
            <div className="grid grid-cols-3 gap-4">
                {RECOMMENDED.map(p => (
                    <motion.div 
                        key={p.id}
                        whileHover={{ y: -5 }}
                        className="relative aspect-video rounded-xl overflow-hidden group cursor-pointer shadow-lg"
                    >
                        <img src={p.cover} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700" />
                        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent flex flex-col justify-end p-4">
                            <span className="text-sm font-bold text-white">{p.name}</span>
                            <span className="text-xs text-neutral-300 opacity-0 group-hover:opacity-100 transition-opacity translate-y-2 group-hover:translate-y-0 duration-300">Daily Mix</span>
                        </div>
                        <div className="absolute top-3 right-3 w-8 h-8 bg-white/20 backdrop-blur-md rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                             <Play size={12} fill="white" className="ml-0.5 text-white" />
                        </div>
                    </motion.div>
                ))}
            </div>
         </section>

         {/* Your Tracks Section */}
         <section>
            <div className="flex justify-between items-center mb-4">
                <h3 className="text-xs font-bold text-neutral-400 uppercase tracking-widest flex items-center gap-2">
                    <Heart size={14} className="text-pink-500"/> Your Collections
                </h3>
                <button className="text-xs text-indigo-400 hover:text-indigo-300 font-medium">See All</button>
            </div>
            <div className="flex gap-4 overflow-x-auto pb-4 scrollbar-hide">
                <motion.div className="w-32 shrink-0 aspect-square rounded-xl border-2 border-dashed border-white/10 flex flex-col items-center justify-center gap-2 text-neutral-500 hover:border-white/30 hover:text-white transition-colors cursor-pointer bg-white/5">
                    <PlusCircle size={24} />
                    <span className="text-xs font-medium">Create New</span>
                </motion.div>
                {YOUR_PLAYLISTS.map(p => (
                    <div key={p.id} className="w-32 shrink-0 group cursor-pointer">
                        <div className="aspect-square rounded-xl overflow-hidden mb-2 relative shadow-md">
                            <img src={p.cover} className="w-full h-full object-cover" />
                            <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                                <Play size={20} fill="white" className="text-white"/>
                            </div>
                        </div>
                        <p className="text-xs font-semibold text-neutral-200 truncate group-hover:text-white transition-colors">{p.name}</p>
                        <p className="text-[10px] text-neutral-500">12 Songs</p>
                    </div>
                ))}
            </div>
         </section>

         {/* Recent List */}
         <section>
            <h3 className="text-xs font-bold text-neutral-400 mb-3 uppercase tracking-widest flex items-center gap-2">
                <List size={14} className="text-indigo-400"/> Recently Played
            </h3>
            <div className="space-y-1">
                {MOCK_SONGS.map((song, i) => (
                    <div 
                        key={song.id}
                        onClick={() => setCurrentSong(song)}
                        className={`flex items-center gap-4 p-3 rounded-xl transition-all cursor-pointer group ${
                            currentSong.id === song.id 
                            ? 'bg-gradient-to-r from-indigo-500/20 to-transparent border border-indigo-500/20' 
                            : 'hover:bg-white/5 border border-transparent'
                        }`}
                    >
                        <span className="w-6 text-center text-xs text-neutral-500 font-mono group-hover:text-white">{i + 1}</span>
                        <img src={song.cover} className="w-10 h-10 rounded-lg object-cover shadow-sm" />
                        <div className="flex-1 min-w-0">
                            <h4 className={`text-sm font-medium truncate ${currentSong.id === song.id ? 'text-indigo-300' : 'text-white'}`}>{song.title}</h4>
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
                    <h3 className="text-sm font-bold text-white">Playing Next</h3>
                    <button 
                        onClick={() => setShowQueue(false)}
                        className="p-1.5 hover:bg-white/10 rounded-lg text-neutral-400 hover:text-white"
                    >
                        <X size={16} />
                    </button>
                </div>
                <div className="flex-1 overflow-y-auto p-2">
                    {/* Fake Queue items repeating MOCK_SONGS */}
                    {[...MOCK_SONGS, ...MOCK_SONGS, ...MOCK_SONGS].map((song, i) => (
                        <div key={i} className={`flex items-center gap-3 p-2 rounded-lg hover:bg-white/5 cursor-pointer ${i===0 ? 'bg-white/5' : ''}`}>
                             <div className="relative w-8 h-8 rounded overflow-hidden shrink-0">
                                 <img src={song.cover} className="w-full h-full object-cover" />
                                 {i===0 && (
                                     <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
                                         <div className="w-1 h-3 bg-indigo-500 rounded-full animate-pulse mx-[1px]"/>
                                         <div className="w-1 h-4 bg-indigo-500 rounded-full animate-pulse mx-[1px] delay-75"/>
                                         <div className="w-1 h-2 bg-indigo-500 rounded-full animate-pulse mx-[1px] delay-150"/>
                                     </div>
                                 )}
                             </div>
                             <div className="min-w-0">
                                 <p className={`text-xs font-medium truncate ${i===0 ? 'text-indigo-300' : 'text-white'}`}>{song.title}</p>
                                 <p className="text-[10px] text-neutral-500 truncate">{song.artist}</p>
                             </div>
                        </div>
                    ))}
                </div>
            </motion.div>
        )}
      </AnimatePresence>

      {/* 
         --- BOTTOM PLAYER BAR ---
      */}
      <div 
        className="absolute bottom-0 left-0 right-0 h-24 bg-[#0f0f0f]/95 backdrop-blur-xl border-t border-white/5 flex items-center justify-between px-6 z-50"
      >
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
                  <h4 className="text-sm font-bold text-white truncate cursor-pointer hover:underline">{currentSong.title}</h4>
                  <p className="text-xs text-neutral-400 truncate cursor-pointer hover:text-neutral-300">{currentSong.artist}</p>
              </div>
          </div>

          {/* CENTER: Controls & Progress */}
          <div className="flex flex-col items-center justify-center gap-2 flex-1 max-w-[40%]">
              {/* Buttons */}
              <div className="flex items-center gap-6">
                  <button className="text-neutral-400 hover:text-white transition-colors">
                      <SkipBack size={20} fill="currentColor"/>
                  </button>
                  <button 
                    onClick={() => setIsPlaying(!isPlaying)}
                    className="w-10 h-10 rounded-full bg-white text-black flex items-center justify-center hover:scale-105 transition-transform shadow-[0_0_15px_rgba(255,255,255,0.2)]"
                  >
                      {isPlaying ? <Pause size={18} fill="currentColor"/> : <Play size={18} fill="currentColor" className="ml-0.5"/>}
                  </button>
                  <button className="text-neutral-400 hover:text-white transition-colors">
                      <SkipForward size={20} fill="currentColor"/>
                  </button>
              </div>
              
              {/* Progress Bar (Integrated) */}
              <div className="w-full flex items-center gap-3">
                  <span className="text-[10px] text-neutral-500 font-mono">1:24</span>
                  <div className="flex-1 h-1 bg-neutral-800 rounded-full cursor-pointer relative group">
                      <div className="absolute inset-y-0 left-0 bg-indigo-500 w-1/3 rounded-full group-hover:bg-indigo-400 transition-colors">
                          <div className="absolute right-0 top-1/2 -translate-y-1/2 w-2 h-2 bg-white rounded-full shadow opacity-0 group-hover:opacity-100 transition-opacity"/>
                      </div>
                  </div>
                  <span className="text-[10px] text-neutral-500 font-mono">{currentSong.duration}</span>
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
    </div>
  );
};
