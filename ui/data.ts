import { TabId, CategoryData, ConfigType } from './types';
import { Zap, Monitor, Code, Palette, Box, Activity, MousePointer2, Shield, Keyboard, Type, Music, Crosshair, Eye, Clock, Hash, Layout, Layers, Command, Grid, Settings, List } from 'lucide-react';

export const APP_DATA: Record<string, CategoryData> = {
  [TabId.OPTIMIZE]: {
    id: TabId.OPTIMIZE,
    title: '性能优化',
    modules: [
      {
        id: 'perf_opt',
        title: '性能优化',
        description: '核心性能调整',
        icon: Zap,
        enabled: true,
        children: [
          { id: 'ignore_armorstand', label: '忽略盔甲架', type: ConfigType.CHECKBOX, value: true },
          { id: 'optimize_entity', label: '实体渲染优化', type: ConfigType.CHECKBOX, value: false },
          { id: 'fast_load', label: '快速加载', type: ConfigType.CHECKBOX, value: true },
          { id: 'entity_limit', label: '实体限制', type: ConfigType.SLIDER, value: 180, min: 10, max: 500, suffix: '个' },
          { id: 'blur_fps', label: '失焦FPS限制', type: ConfigType.SLIDER, value: 30, min: 5, max: 60, suffix: ' FPS' },
          { id: 'particle_limit', label: '粒子限制', type: ConfigType.SLIDER, value: 100, min: 0, max: 2000, suffix: '' },
          { id: 'font_opt', label: '字体优化', type: ConfigType.CHECKBOX, value: false },
          { id: 'static_particle', label: '静态粒子颜色', type: ConfigType.CHECKBOX, value: true },
          { id: 'chunk_load_limit', label: '限制区块加载', type: ConfigType.CHECKBOX, value: true },
          { id: 'chunk_update', label: '区块更新限制', type: ConfigType.SLIDER, value: 50, min: 1, max: 100, suffix: 'ms' },
        ]
      },
      {
        id: 'smooth_zoom',
        title: '平滑缩放',
        description: '优化变焦体验',
        icon: Eye,
        enabled: false,
        children: [
          { id: 'zoom_smooth', label: '缩放平滑', type: ConfigType.CHECKBOX, value: false },
          { id: 'zoom_speed', label: '速度', type: ConfigType.SLIDER, value: 4.0, min: 1.0, max: 10.0, step: 0.1 },
          { id: 'mouse_smooth', label: '鼠标平滑', type: ConfigType.CHECKBOX, value: false },
        ]
      },
      {
        id: 'old_anim',
        title: '旧动画',
        description: '1.7 风格动画还原',
        icon: Activity,
        enabled: false,
        children: [
          { id: 'no_shield', label: '不显示盾牌', type: ConfigType.CHECKBOX, value: true },
          { id: 'sneak_anim', label: '潜行动画', type: ConfigType.CHECKBOX, value: true },
          { id: 'old_rod', label: '旧鱼竿', type: ConfigType.CHECKBOX, value: true },
          { id: 'old_bow', label: '旧弓', type: ConfigType.CHECKBOX, value: true },
          { id: 'old_swing', label: '旧挥动', type: ConfigType.CHECKBOX, value: true },
          { id: 'block_swing', label: '格挡挥动', type: ConfigType.CHECKBOX, value: true },
          { id: 'old_dmg', label: '旧伤害动画', type: ConfigType.CHECKBOX, value: true },
          { id: 'old_use', label: '旧使用动画', type: ConfigType.CHECKBOX, value: true },
          { id: 'old_block', label: '旧格挡', type: ConfigType.CHECKBOX, value: true },
          { id: 'anim_x', label: 'X', type: ConfigType.SLIDER, value: 0.0, min: -2, max: 2, step: 0.1 },
          { id: 'anim_y', label: 'Y', type: ConfigType.SLIDER, value: 0.0, min: -2, max: 2, step: 0.1 },
          { id: 'anim_z', label: 'Z', type: ConfigType.SLIDER, value: 0.0, min: -2, max: 2, step: 0.1 },
        ]
      },
      {
        id: 'no_hurt_cam',
        title: '无受伤动画',
        description: '移除受伤视口抖动',
        icon: Shield,
        enabled: false,
        children: []
      },
      {
        id: 'fix_inventory',
        title: '固定物品栏',
        description: '防止物品栏渲染抖动',
        icon: Layout,
        enabled: false,
        children: []
      },
      {
        id: 'no_hit_delay',
        title: '无打击延迟',
        description: '移除1.8+攻击冷却视觉',
        icon: MousePointer2,
        enabled: false,
        children: []
      }
    ]
  },
  [TabId.RENDER]: {
    id: TabId.RENDER,
    title: '视觉渲染',
    modules: [
      {
        id: 'motion_blur',
        title: '运动模糊',
        description: '模拟动态视觉残留',
        icon: Activity,
        enabled: false,
        children: [
            { id: 'mb_amount', label: '模糊倍数', type: ConfigType.SLIDER, value: 2.0, min: 0.1, max: 10.0, step: 0.1 }
        ]
      },
      { id: 'full_bright', title: '保持亮度', description: '永久夜视效果', icon: Eye, enabled: false, children: [] },
      { id: 'item_physic', title: '物品物理', description: '掉落物物理效果', icon: Box, enabled: false, children: [] },
      { id: 'min_bob', title: '最小摇晃', description: '减少手臂摇晃幅度', icon: Activity, enabled: false, children: [] },
      {
        id: 'more_particles',
        title: '更多粒子',
        description: '增强粒子特效',
        icon: Zap,
        enabled: false,
        children: [
            { id: 'sharp_p', label: '锋利粒子', type: ConfigType.SLIDER, value: 2.0, min: 1.0, max: 10.0 },
            { id: 'always_sharp', label: '总是锋利粒子', type: ConfigType.CHECKBOX, value: false },
            { id: 'crit_p', label: '暴击粒子', type: ConfigType.SLIDER, value: 2.0, min: 1.0, max: 10.0 },
            { id: 'always_crit', label: '总是暴击粒子', type: ConfigType.CHECKBOX, value: false },
        ]
      },
      { id: 'hit_color', title: '击中颜色', description: '自定义受伤变色', icon: Palette, enabled: false, children: [] },
      {
        id: 'block_overlay',
        title: '方块高亮',
        description: '指向方块增强显示',
        icon: Box,
        enabled: false,
        children: [
            { id: 'bo_fill', label: '填充', type: ConfigType.CHECKBOX, value: true },
            { id: 'bo_outline', label: '描边', type: ConfigType.CHECKBOX, value: true },
            { id: 'bo_width', label: '描边宽度', type: ConfigType.SLIDER, value: 1.0, min: 0.1, max: 5.0, step: 0.1 },
            { id: 'bo_through', label: '穿透方块', type: ConfigType.CHECKBOX, value: false },
        ]
      },
      {
          id: 'dragon_wings',
          title: '龙翅膀',
          description: '玩家背部装饰',
          icon: Zap, 
          enabled: false,
          children: [
              { id: 'dw_size', label: '大小', type: ConfigType.SLIDER, value: 63.0, min: 10, max: 100 },
              { id: 'dw_color', label: '翅膀上色', type: ConfigType.CHECKBOX, value: true },
              { id: 'dw_chroma', label: '彩色', type: ConfigType.CHECKBOX, value: true },
          ]
      },
      {
          id: 'fire_mod',
          title: '火焰修改',
          description: '低火焰视线遮挡',
          icon: Activity,
          enabled: false,
          children: [
              { id: 'fire_height', label: '高度', type: ConfigType.SLIDER, value: 0.2, min: 0.0, max: 1.0, step: 0.1 },
              { id: 'fire_custom', label: '自定义颜色', type: ConfigType.CHECKBOX, value: false },
          ]
      },
      { id: 'free_look', title: '自由视角', description: '360度观察', icon: Eye, enabled: false, children: [] },
      { id: 'hitbox', title: '碰撞箱', description: '显示实体碰撞体积', icon: Box, enabled: false, children: [] },
      {
          id: 'crosshair',
          title: '准星',
          description: '自定义屏幕准星',
          icon: Crosshair,
          enabled: false,
          children: [
            { id: 'ch_dynamic', label: '动态范围', type: ConfigType.SLIDER, value: 4.0, min: 0, max: 10 },
            { id: 'ch_outline', label: '描边', type: ConfigType.CHECKBOX, value: true },
            { id: 'ch_len', label: '描边长度', type: ConfigType.SLIDER, value: 1.0, min: 0, max: 5 },
            { id: 'ch_gap', label: '间隔', type: ConfigType.SLIDER, value: 6.0, min: 0, max: 10 },
            { id: 'ch_thick', label: '粗细', type: ConfigType.SLIDER, value: 0.6, min: 0.1, max: 5, step: 0.1 },
            { id: 'ch_length', label: '长度', type: ConfigType.SLIDER, value: 3.5, min: 0, max: 10, step: 0.1 },
          ]
      },
      { id: 'dmg_ind', title: '伤害指示器', description: '显示造成的伤害数值', icon: Activity, enabled: false, children: [] }
    ]
  },
  [TabId.TOOLS]: {
    id: TabId.TOOLS,
    title: '辅助工具',
    modules: [
        { id: 'sprint', title: '强制疾跑', description: '自动保持疾跑状态', icon: Activity, enabled: false, children: [] },
        { 
            id: 'client_chat', 
            title: '客户端聊天', 
            description: '优化聊天体验',
            icon: Type, 
            enabled: false, 
            children: [
                { id: 'cc_icon', label: '显示同客户端用户', type: ConfigType.CHECKBOX, value: true }
            ] 
        },
        {
            id: 'skin_mod',
            title: '皮肤修改器',
            description: '本地皮肤替换',
            icon: Palette,
            enabled: false,
            children: [
                { id: 'sm_name', label: '皮肤名称', type: ConfigType.INPUT, value: '', placeholder: '输入ID...' }
            ]
        },
        {
            id: 'time_changer',
            title: '时间修改',
            description: '锁定世界时间',
            icon: Clock,
            enabled: false,
            children: [
                { id: 'tc_val', label: '时间', type: ConfigType.SLIDER, value: 0, min: 0, max: 24000, suffix: '' }
            ]
        },
        {
            id: 'tnt_timer',
            title: 'TNT时间显示',
            description: '显示爆炸倒计时',
            icon: Clock,
            enabled: false,
            children: [
                { id: 'tnt_time', label: 'TNT爆炸时间', type: ConfigType.SLIDER, value: 4.0, min: 0, max: 10, step: 0.1, suffix: 's' }
            ]
        },
        {
            id: 'nametags',
            title: '名字标签',
            description: '增强玩家头顶显示',
            icon: Type,
            enabled: false,
            children: [
                { id: 'nt_self', label: '显示自己标签', type: ConfigType.CHECKBOX, value: true },
                { id: 'nt_health', label: '显示血条', type: ConfigType.CHECKBOX, value: true },
            ]
        },
        {
            id: 'fov_changer',
            title: '视场角',
            description: '动态FOV控制',
            icon: Eye,
            enabled: false,
            children: [
                { id: 'no_speed_fov', label: '无速度变化', type: ConfigType.CHECKBOX, value: false },
                { id: 'no_fly_fov', label: '无飞行变化', type: ConfigType.CHECKBOX, value: false },
                { id: 'no_bow_fov', label: '无弓箭变化', type: ConfigType.CHECKBOX, value: false },
            ]
        },
        {
            id: 'name_protect',
            title: '名字保护',
            description: '隐藏或伪装玩家名',
            icon: Shield,
            enabled: false,
            children: [
                { id: 'np_fake', label: '假名字', type: ConfigType.INPUT, value: 'Hide', placeholder: 'Name' }
            ]
        },
        { id: 'raw_input', title: '原始输入', description: '无加速度鼠标输入', icon: MousePointer2, enabled: false, children: [] },
        {
            id: 'client_cmd',
            title: '客户端命令',
            description: '自定义命令配置',
            icon: Command,
            enabled: false,
            children: [
                { id: 'cmd_prefix', label: '命令前缀', type: ConfigType.INPUT, value: '#', placeholder: '#' }
            ]
        }
    ]
  },
  [TabId.INTERFACE]: {
    id: TabId.INTERFACE,
    title: '界面定制',
    modules: [
        {
            id: 'client_set',
            title: '客户端设置',
            description: '全局UI调整',
            icon: Settings,
            enabled: true,
            children: [
                { id: 'fix_scale', label: '固定界面缩放', type: ConfigType.CHECKBOX, value: true },
                { id: 'blur_gui', label: '界面组件模糊', type: ConfigType.CHECKBOX, value: true },
            ]
        },
        {
            id: 'better_gui',
            title: '更好的界面',
            description: '现代化菜单风格',
            icon: Layout,
            enabled: true,
            children: [
                { id: 'bg_back', label: '开启背景', type: ConfigType.CHECKBOX, value: true },
                { id: 'bg_anim', label: '背景动画', type: ConfigType.CHECKBOX, value: true },
                { id: 'bg_flicker', label: '防止闪烁', type: ConfigType.CHECKBOX, value: true },
            ]
        },
        // Reusable HUD Component Config Generator
        ...[
            { id: 'fps_hud', title: '帧数显示' },
            { id: 'armor_hud', title: '护甲显示' },
            { id: 'combo_hud', title: '连击显示' },
            { id: 'cps_hud', title: '点击速度显示' },
            { id: 'pot_hud', title: '药水显示' },
            { id: 'reach_hud', title: '攻击距离显示' },
            { id: 'ping_hud', title: '延迟显示' },
            { id: 'coord_hud', title: '坐标显示', extra: [
                { id: 'h_limit', label: '高度限制显示', type: ConfigType.CHECKBOX, value: false },
                { id: 'h_val', label: '高度限制', type: ConfigType.SLIDER, value: 92.0, min: 0, max: 320 }
            ]},
        ].map(hud => ({
            id: hud.id,
            title: hud.title,
            icon: Grid,
            enabled: false,
            children: [
                { id: `${hud.id}_shadow`, label: '字体阴影', type: ConfigType.CHECKBOX, value: true },
                { id: `${hud.id}_font`, label: '更好字体', type: ConfigType.CHECKBOX, value: false },
                { id: `${hud.id}_bg`, label: '背景', type: ConfigType.CHECKBOX, value: true },
                { id: `${hud.id}_radius_en`, label: '背景圆角', type: ConfigType.CHECKBOX, value: true },
                { id: `${hud.id}_radius`, label: '圆角半径', type: ConfigType.SLIDER, value: 3.0, min: 0, max: 10, suffix: 'px' },
                ...(hud.extra || []) as any[]
            ]
        })),
        {
            id: 'chat_hud',
            title: '聊天框',
            icon: Type,
            enabled: false,
            children: [
                { id: 'ch_shadow', label: '字体阴影', type: ConfigType.CHECKBOX, value: true },
                { id: 'ch_font', label: '更好字体', type: ConfigType.CHECKBOX, value: false },
                { id: 'ch_bg', label: '背景', type: ConfigType.CHECKBOX, value: true },
            ]
        },
        {
            id: 'scoreboard',
            title: '计分板',
            icon: List,
            enabled: false,
            children: [
                { id: 'sb_shadow', label: '字体阴影', type: ConfigType.CHECKBOX, value: true },
                { id: 'sb_font', label: '更好字体', type: ConfigType.CHECKBOX, value: false },
                { id: 'sb_red', label: '红字', type: ConfigType.CHECKBOX, value: false },
                { id: 'sb_bg', label: '背景', type: ConfigType.CHECKBOX, value: true },
                { id: 'sb_radius_en', label: '圆角', type: ConfigType.CHECKBOX, value: true },
                { id: 'sb_radius', label: '半径', type: ConfigType.SLIDER, value: 3.0, min: 0, max: 10 },
            ]
        },
        {
             id: 'music_hud',
             title: '音乐显示',
             icon: Music,
             enabled: false,
             children: [
                 { id: 'mh_vis', label: '可视化幅度', type: ConfigType.SLIDER, value: 1.9, min: 0.1, max: 5.0, step: 0.1 },
                 { id: 'mh_font', label: '更好字体', type: ConfigType.CHECKBOX, value: true },
                 { id: 'mh_shadow', label: '字体阴影', type: ConfigType.CHECKBOX, value: true },
             ]
        },
        {
            id: 'lyrics_hud',
            title: '歌词显示',
            icon: Type,
            enabled: false,
            children: [
                { id: 'lh_font', label: '更好字体', type: ConfigType.CHECKBOX, value: true },
                { id: 'lh_bg', label: '背景', type: ConfigType.CHECKBOX, value: true },
                { id: 'lh_rad', label: '圆角', type: ConfigType.CHECKBOX, value: true },
                { id: 'lh_rad_val', label: '半径', type: ConfigType.SLIDER, value: 3.0, min: 0, max: 10 },
            ]
        },
        {
            id: 'key_hud',
            title: '按键显示',
            icon: Keyboard,
            enabled: false,
            children: [
                { id: 'kh_shadow', label: '字体阴影', type: ConfigType.CHECKBOX, value: true },
                { id: 'kh_font', label: '更好字体', type: ConfigType.CHECKBOX, value: false },
                { id: 'kh_bg', label: '背景', type: ConfigType.CHECKBOX, value: true },
                { id: 'kh_rad', label: '圆角', type: ConfigType.CHECKBOX, value: true },
                { id: 'kh_rad_val', label: '半径', type: ConfigType.SLIDER, value: 3.0, min: 0, max: 10 },
            ]
        },
        {
            id: 'inv_hud',
            title: '物品栏显示',
            icon: Box,
            enabled: false,
            children: [
                { id: 'ih_bg', label: '背景', type: ConfigType.CHECKBOX, value: true },
                { id: 'ih_rad', label: '圆角', type: ConfigType.CHECKBOX, value: true },
                { id: 'ih_rad_val', label: '半径', type: ConfigType.SLIDER, value: 3.0, min: 0, max: 10 },
            ]
        },
        {
            id: 'player_hud',
            title: '玩家显示',
            icon: Activity,
            enabled: false,
            children: [
                { id: 'ph_font', label: '更好字体', type: ConfigType.CHECKBOX, value: false },
                { id: 'ph_shadow', label: '字体阴影', type: ConfigType.CHECKBOX, value: true },
                { id: 'ph_bg', label: '背景', type: ConfigType.CHECKBOX, value: true },
                { id: 'ph_rad_val', label: '半径', type: ConfigType.SLIDER, value: 3.0, min: 0, max: 10 },
            ]
        },
        { id: 'target_hud', title: '目标显示', icon: Crosshair, enabled: false, children: [] },
        {
            id: 'feature_list',
            title: '功能列表',
            icon: List,
            enabled: false,
            children: [
                { id: 'fl_logo', label: '显示客户端标志', type: ConfigType.CHECKBOX, value: true },
                { id: 'fl_eng', label: '显示英文名', type: ConfigType.CHECKBOX, value: true },
                { id: 'fl_color', label: '彩色列表', type: ConfigType.CHECKBOX, value: true },
                { id: 'fl_font', label: '更好字体', type: ConfigType.CHECKBOX, value: false },
                { id: 'fl_bg', label: '背景', type: ConfigType.CHECKBOX, value: true },
            ]
        },
        { id: 'minimap', title: '小地图', icon: Grid, enabled: false, children: [] },
        { id: 'direction_hud', title: '方向显示', icon: Crosshair, enabled: false, children: [] }
    ]
  }
};