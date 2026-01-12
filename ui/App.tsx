import React, { useState } from 'react';
import { Sidebar } from './components/Sidebar';
import { SettingsPanel } from './components/SettingsPanel';
import { TabId } from './types';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronDown, SkipBack, Play, Pause, SkipForward, Repeat, Shuffle, Heart } from 'lucide-react';

const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabId>(TabId.OPTIMIZE);
  const [immersiveMode, setImmersiveMode] = useState(false);

  // App startup animation variants
  const containerVariants = {
    hidden: { 
        opacity: 0, 
        scale: 0.92,
        filter: "blur(10px)"
    },
    visible: { 
        opacity: 1, 
        scale: 1,
        filter: "blur(0px)",
        transition: { 
            duration: 0.6, 
            ease: [0.22, 1, 0.36, 1],
            when: "beforeChildren",
            staggerChildren: 0.1
        } 
    }
  };

  const childVariants = {
      hidden: { opacity: 0, y: 20 },
      visible: { opacity: 1, y: 0, transition: { duration: 0.4 } }
  };

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center font-sans antialiased selection:bg-indigo-500/30 text-slate-200 p-4">
      
      {/* Page Level Branding (Fixed to viewport) */}
      <div className="fixed bottom-8 left-8 z-0 pointer-events-none select-none">
          <motion.div 
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.8, duration: 0.8 }}
          >
              <div className="text-2xl font-bold text-white tracking-tight drop-shadow-lg">
                  FPSMaster <span className="text-indigo-400">Nova</span>
              </div>
              <div className="mt-2 flex">
                  <span className="px-2 py-0.5 rounded-md bg-white/5 text-xs font-mono text-neutral-400 border border-white/5 backdrop-blur-sm">
                      4.0.0 beta
                  </span>
              </div>
          </motion.div>
      </div>

      {/* Main Window */}
      <motion.div 
        initial="hidden"
        animate="visible"
        variants={containerVariants}
        className="w-[950px] h-[620px] bg-[#0a0a0a]/90 backdrop-blur-2xl border border-white/10 rounded-xl shadow-[0_25px_50px_-12px_rgba(0,0,0,0.5)] flex overflow-hidden ring-1 ring-white/5 relative z-10"
      >
        {/* Decor */}
        <div className="absolute top-0 right-0 w-[400px] h-[400px] bg-indigo-600/10 rounded-full blur-[100px] translate-x-1/2 -translate-y-1/2 pointer-events-none" />
        
        {/* Sidebar */}
        <motion.div variants={childVariants} className="shrink-0 border-r border-white/5 bg-black/20 h-full">
            <Sidebar activeTab={activeTab} setActiveTab={setActiveTab} />
        </motion.div>

        {/* Content */}
        <motion.div variants={childVariants} className="flex-1 relative min-w-0 flex flex-col h-full">
            <SettingsPanel activeTab={activeTab} setImmersiveMode={setImmersiveMode} />
        </motion.div>

        {/* Immersive Music Overlay - Renders ON TOP of Sidebar and Content */}
        <AnimatePresence>
            {immersiveMode && (
                <motion.div 
                    initial={{ y: '100%' }}
                    animate={{ y: 0 }}
                    exit={{ y: '100%' }}
                    transition={{ type: 'spring', damping: 25, stiffness: 200 }}
                    className="absolute inset-0 z-[100] bg-neutral-900 flex flex-col"
                >
                     {/* Blurred Backdrop */}
                     <div className="absolute inset-0 z-0">
                         <img src="https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=400" className="w-full h-full object-cover opacity-30 blur-[100px]" />
                         <div className="absolute inset-0 bg-black/40" />
                     </div>

                     {/* Content */}
                     <div className="relative z-10 flex flex-col h-full p-8">
                         {/* Header */}
                         <div className="flex justify-between items-center text-white/50 mb-8">
                            <button onClick={() => setImmersiveMode(false)} className="hover:text-white hover:bg-white/10 p-2 rounded-full transition-all">
                                <ChevronDown size={32} />
                            </button>
                            <span className="text-xs font-bold tracking-widest uppercase bg-white/10 px-3 py-1 rounded-full text-white">Immersive Audio</span>
                         </div>

                         {/* Center Stage */}
                         <div className="flex-1 flex items-center justify-center gap-16 px-12">
                             {/* Big Art */}
                             <motion.div 
                                layoutId="mini-cover" 
                                className="w-[350px] aspect-square rounded-2xl overflow-hidden shadow-[0_30px_60px_-10px_rgba(0,0,0,0.6)] border border-white/10"
                             >
                                 <img src="https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=600" className="w-full h-full object-cover" />
                             </motion.div>

                             {/* Lyrics/Info */}
                             <div className="flex-1 max-w-md space-y-8">
                                 <div>
                                     <motion.h1 className="text-4xl font-bold text-white mb-2 leading-tight">Midnight City</motion.h1>
                                     <motion.p className="text-2xl text-indigo-300">M83</motion.p>
                                 </div>
                                 <div className="space-y-4 text-lg font-medium text-white/40 mask-image-linear-gradient h-[200px] overflow-hidden">
                                     <p className="text-white scale-105 origin-left">Waiting in a car</p>
                                     <p>Waiting for a ride in the dark</p>
                                     <p>The night city grows</p>
                                     <p>Look and see her eyes</p>
                                 </div>
                             </div>
                         </div>

                         {/* Footer Controls */}
                         <div className="mt-8 space-y-6 max-w-3xl mx-auto w-full">
                             {/* Progress */}
                             <div className="w-full h-1.5 bg-white/20 rounded-full cursor-pointer overflow-hidden">
                                 <div className="w-1/3 h-full bg-white shadow-[0_0_15px_white]" />
                             </div>
                             
                             <div className="flex items-center justify-between">
                                 <Shuffle size={24} className="text-white/40 hover:text-white cursor-pointer" />
                                 <div className="flex items-center gap-8">
                                     <SkipBack size={32} className="fill-white text-white hover:scale-110 transition-transform cursor-pointer" />
                                     <div className="w-20 h-20 rounded-full bg-white flex items-center justify-center text-black hover:scale-105 transition-transform cursor-pointer shadow-[0_0_30px_rgba(255,255,255,0.3)]">
                                         <Pause size={32} fill="currentColor" />
                                     </div>
                                     <SkipForward size={32} className="fill-white text-white hover:scale-110 transition-transform cursor-pointer" />
                                 </div>
                                 <div className="flex gap-6">
                                    <Heart size={24} className="text-indigo-400 hover:scale-110 transition-transform cursor-pointer" />
                                    <Repeat size={24} className="text-white/40 hover:text-white cursor-pointer" />
                                 </div>
                             </div>
                         </div>
                     </div>
                </motion.div>
            )}
        </AnimatePresence>

      </motion.div>
    </div>
  );
};

export default App;