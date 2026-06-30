import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';

export type Locale = 'en' | 'zh';

type Dict = Record<string, string>;

// Static UI-chrome translations. Module / setting / option labels are translated
// on the client (Kotlin) side and arrive via packets; this dictionary only covers
// the React UI's own static text.
const ZH: Dict = {
  // navigation / sidebar
  'nav.optimize': '优化配置',
  'nav.render': '视觉渲染',
  'nav.tools': '辅助工具',
  'nav.interface': '界面定制',
  'nav.music': '媒体中心',
  'nav.settings': '全局设置',
  'nav.collapse': '收起菜单',

  // category header titles
  'tab.optimize': '性能优化',
  'tab.render': '视觉渲染',
  'tab.tools': '实用工具',
  'tab.interface': '界面功能',
  'tab.music': '音乐',
  'tab.settings': '设置',

  // common
  'common.clear': '清除',
  'common.select': '请选择',
  'common.none': '无',
  'common.cancel': '取消',
  'common.confirm': '确认',
  'common.load': '加载',
  'common.delete': '删除',
  'common.refresh': '刷新',

  // background options (shared by OOBE + settings)
  'bg.panorama_1': '全景 I',
  'bg.panorama_2': '全景 II',
  'bg.panorama_3': '全景 III',
  'bg.classic': '纯色',
  'bg.shader': '动态',
  'bg.custom': '自定义',

  // OOBE
  'oobe.subtitle': '完成首次客户端偏好设置',
  'oobe.anonymous.title': '匿名数据',
  'oobe.anonymous.desc': '允许上传匿名性能与使用数据，用于改善客户端稳定性。',
  'oobe.anonymous.toggle': '启用上报',
  'oobe.safety.title': '安全偏好',
  'oobe.safety.desc': '选择是否启用客户端反作弊相关偏好。',
  'oobe.safety.toggle': '反作弊偏好',
  'oobe.background.title': '主菜单背景',
  'oobe.background.desc': '选择启动后标题界面的默认背景表现。',
  'oobe.finish': '完成',
  'oobe.enterSettings': '进入设置',

  // settings panel chrome
  'settings.noModules': '当前分类暂无模块',
  'settings.syncingModules': '正在同步模块列表...',
  'settings.clientOptions': '客户端选项',
  'settings.anonymousReport': '匿名数据上报',
  'settings.waitingSync': '等待同步',
  'settings.oobeCompleted': '已完成首次引导',
  'settings.antiCheatPref': '反作弊偏好',
  'settings.menuBackground': '主菜单背景',
  'settings.webviewMetrics': 'WebView 性能指标',
  'settings.webviewHwAccel': 'WebView 硬件加速',
  'settings.editHud': 'HUD 自定义编辑',
  'settings.editHudButton': '打开编辑器',
  'settings.hwConfirm': '确认画面正常，{seconds}s 后自动回退',
  'settings.configProfiles': '配置档案',
  'settings.currentProfile': '当前: {name}',
  'settings.unknown': '未知',
  'settings.profileCount': '{count} 个配置档案',
  'settings.syncingProfiles': '正在同步配置档案',
  'settings.saveCurrent': '保存当前',
  'settings.currentSuffix': ' · 当前',
  'settings.profileNamePlaceholder': '配置名',
  'settings.create': '创建',
  'settings.saveAs': '另存为',
  'settings.renamePlaceholder': '重命名为',
  'settings.rename': '重命名',
  'settings.disableAll': '全部关闭',
  'settings.ioPathPlaceholder': '导入/导出路径',
  'settings.import': '导入',
  'settings.exportCurrent': '导出当前',
  'settings.moduleInDev': '该模块正在开发中...',
  'settings.globalSettings': '全局设置',

  // music player
  'music.title': '音乐',
  'music.tab.discover': '发现',
  'music.tab.library': '我的',
  'music.tab.radio': '电台',
  'music.searchPlaceholder': '搜索...',
  'music.login': '登录',
  'music.qrLogin': '扫码登录',
  'music.qrExpired': '二维码已过期',
  'music.qrScanned': '扫码成功!',
  'music.confirmOnPhone': '请在手机上确认',
  'music.qrOpenApp': '请打开网易云音乐APP',
  'music.qrWaiting': '等待确认...',
  'music.qrLoggingIn': '登录中...',
  'music.qrScanPrompt': '扫码登录网易云音乐',
  'music.secureLogin': '安全登录',
  'music.confirmLogout': '确认退出登录?',
  'music.logoutDesc': '退出后将无法访问您的私人歌单和日推。',
  'music.logout': '退出',
  'music.dailyPlaylist': '每日推荐歌单',
  'music.recommendPlaylist': '推荐歌单',
  'music.dailyRecommend': '每日推荐',
  'music.loadingRecommend': '加载推荐歌单中...',
  'music.loginForDaily': '登录查看每日推荐歌单',
  'music.yourFavorites': '你的收藏',
  'music.viewAll': '查看全部',
  'music.newPlaylist': '新建歌单',
  'music.songsCount': '{count} 首歌曲',
  'music.loginForPlaylists': '登录查看收藏歌单',
  'music.recentPlayed': '最近播放',
  'music.loadingPlaylist': '加载歌单中...',
  'music.emptyPlaylist': '歌单为空',
  'music.loadingDaily': '加载每日推荐中...',
  'music.loginForDailySongs': '登录查看每日推荐歌曲',
  'music.queue': '播放队列',
  'music.queueEmpty': '队列为空',
  'music.noLyrics': '暂无歌词',
};

const EN: Dict = {
  'nav.optimize': 'Optimization',
  'nav.render': 'Rendering',
  'nav.tools': 'Tools',
  'nav.interface': 'Interface',
  'nav.music': 'Media',
  'nav.settings': 'Settings',
  'nav.collapse': 'Collapse',

  'tab.optimize': 'Performance',
  'tab.render': 'Rendering',
  'tab.tools': 'Tools',
  'tab.interface': 'Interface',
  'tab.music': 'Music',
  'tab.settings': 'Settings',

  'common.clear': 'Clear',
  'common.select': 'Select',
  'common.none': 'None',
  'common.cancel': 'Cancel',
  'common.confirm': 'Confirm',
  'common.load': 'Load',
  'common.delete': 'Delete',
  'common.refresh': 'Refresh',

  'bg.panorama_1': 'Panorama I',
  'bg.panorama_2': 'Panorama II',
  'bg.panorama_3': 'Panorama III',
  'bg.classic': 'Solid',
  'bg.shader': 'Dynamic',
  'bg.custom': 'Custom',

  'oobe.subtitle': 'Complete your first-time client setup',
  'oobe.anonymous.title': 'Anonymous Data',
  'oobe.anonymous.desc': 'Allow uploading anonymous performance and usage data to help improve client stability.',
  'oobe.anonymous.toggle': 'Enable reporting',
  'oobe.safety.title': 'Safety Preferences',
  'oobe.safety.desc': 'Choose whether to enable client anti-cheat preferences.',
  'oobe.safety.toggle': 'Anti-cheat preference',
  'oobe.background.title': 'Main Menu Background',
  'oobe.background.desc': 'Choose the default background for the title screen.',
  'oobe.finish': 'Finish',
  'oobe.enterSettings': 'Enter settings',

  'settings.noModules': 'No modules in this category',
  'settings.syncingModules': 'Syncing module list...',
  'settings.clientOptions': 'Client Options',
  'settings.anonymousReport': 'Anonymous Data Reporting',
  'settings.waitingSync': 'Waiting for sync',
  'settings.oobeCompleted': 'First-run setup completed',
  'settings.antiCheatPref': 'Anti-cheat preference',
  'settings.menuBackground': 'Main Menu Background',
  'settings.webviewMetrics': 'WebView Performance Metrics',
  'settings.webviewHwAccel': 'WebView Hardware Acceleration',
  'settings.editHud': 'HUD Layout Editor',
  'settings.editHudButton': 'Open Editor',
  'settings.hwConfirm': 'Confirm the display is OK; reverting in {seconds}s',
  'settings.configProfiles': 'Config Profiles',
  'settings.currentProfile': 'Current: {name}',
  'settings.unknown': 'Unknown',
  'settings.profileCount': '{count} profiles',
  'settings.syncingProfiles': 'Syncing config profiles',
  'settings.saveCurrent': 'Save Current',
  'settings.currentSuffix': ' · current',
  'settings.profileNamePlaceholder': 'Profile name',
  'settings.create': 'Create',
  'settings.saveAs': 'Save As',
  'settings.renamePlaceholder': 'Rename to',
  'settings.rename': 'Rename',
  'settings.disableAll': 'Disable All',
  'settings.ioPathPlaceholder': 'Import/Export path',
  'settings.import': 'Import',
  'settings.exportCurrent': 'Export Current',
  'settings.moduleInDev': 'This module is under development...',
  'settings.globalSettings': 'Global Settings',

  'music.title': 'Music',
  'music.tab.discover': 'Discover',
  'music.tab.library': 'Library',
  'music.tab.radio': 'Radio',
  'music.searchPlaceholder': 'Search...',
  'music.login': 'Log In',
  'music.qrLogin': 'Scan to Log In',
  'music.qrExpired': 'QR code expired',
  'music.qrScanned': 'Scanned!',
  'music.confirmOnPhone': 'Please confirm on your phone',
  'music.qrOpenApp': 'Please open the NetEase Cloud Music app',
  'music.qrWaiting': 'Waiting for confirmation...',
  'music.qrLoggingIn': 'Logging in...',
  'music.qrScanPrompt': 'Scan to log in to NetEase Cloud Music',
  'music.secureLogin': 'Secure login',
  'music.confirmLogout': 'Log out?',
  'music.logoutDesc': "You'll lose access to your private playlists and daily recommendations.",
  'music.logout': 'Log out',
  'music.dailyPlaylist': 'Daily Playlists',
  'music.recommendPlaylist': 'Recommended Playlists',
  'music.dailyRecommend': 'Daily Recommend',
  'music.loadingRecommend': 'Loading recommended playlists...',
  'music.loginForDaily': 'Log in to see daily playlists',
  'music.yourFavorites': 'Your Favorites',
  'music.viewAll': 'View All',
  'music.newPlaylist': 'New Playlist',
  'music.songsCount': '{count} songs',
  'music.loginForPlaylists': 'Log in to view saved playlists',
  'music.recentPlayed': 'Recently Played',
  'music.loadingPlaylist': 'Loading playlist...',
  'music.emptyPlaylist': 'Playlist is empty',
  'music.loadingDaily': 'Loading daily recommendations...',
  'music.loginForDailySongs': 'Log in to see daily songs',
  'music.queue': 'Play Queue',
  'music.queueEmpty': 'Queue is empty',
  'music.noLyrics': 'No lyrics',
};

const TABLES: Record<Locale, Dict> = { zh: ZH, en: EN };

export type TranslateFn = (key: string, params?: Record<string, string | number>) => string;

const translate = (locale: Locale, key: string, params?: Record<string, string | number>): string => {
  let value = TABLES[locale]?.[key] ?? ZH[key] ?? key;
  if (params) {
    for (const [name, replacement] of Object.entries(params)) {
      value = value.split(`{${name}}`).join(String(replacement));
    }
  }
  return value;
};

interface LocaleContextValue {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: TranslateFn;
}

const LocaleContext = createContext<LocaleContextValue | null>(null);

export const LocaleProvider: React.FC<{ children: React.ReactNode; initialLocale?: Locale }> = ({
  children,
  initialLocale = 'zh',
}) => {
  const [locale, setLocale] = useState<Locale>(initialLocale);
  const t = useCallback<TranslateFn>((key, params) => translate(locale, key, params), [locale]);
  const value = useMemo<LocaleContextValue>(() => ({ locale, setLocale, t }), [locale, t]);
  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
};

const useLocaleContext = (): LocaleContextValue => {
  const ctx = useContext(LocaleContext);
  if (!ctx) {
    throw new Error('useT / useLocale must be used within a LocaleProvider');
  }
  return ctx;
};

export const useT = (): TranslateFn => useLocaleContext().t;
export const useLocale = (): Locale => useLocaleContext().locale;
export const useSetLocale = (): ((locale: Locale) => void) => useLocaleContext().setLocale;
