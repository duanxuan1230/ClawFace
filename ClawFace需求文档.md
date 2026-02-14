# ClawFace 技术产品文档 (PRD)
项目代号：ClawFace (OpenClaw Android Output)
核心目标：构建一个极致轻量、全程序化生成的安卓悬浮“桌宠”，作为 OpenClaw 的远程情感显示终端。

## 1. 移动端需求 (Android Client)
技术栈推荐：Kotlin, Android Native Canvas, ViewBinding, Coroutines (UDP/TCP)。

**1.1 UI 与 视觉设计 (Airy UI)**
 * 设计理念：去边框化、高通透度、呼吸感。
 * 实现细节：
   * Window Type：TYPE_APPLICATION_OVERLAY，背景全透明 (PixelFormat.TRANSLUCENT)。
   * 渲染风格：
     * 不使用位图 (Bitmap)，全矢量绘制 (Path/Paint)。
     * 线条带有微弱的发光效果 (ShadowLayer/MaskFilter)。
     * 动态模糊：在表情快速变化时增加运动模糊感（可选）。
 * 交互：
   * 交互模式：长按激活，允许拖拽位置。

**1.2 视觉表现系统 (Visual System)**

情绪、表情、动作互相配合呈现
 * 情绪配色 (Mood Color System)：
   * 机制：基于 HSB/ARGB 的颜色插值算法。
   * 预设库（详见附录）：
Neutral 日常/待机、Anxiety 焦虑、Envy 羡慕、Embarrassment 耻辱/尴尬、Ennui 厌倦/无聊、Disgust 恶心、Fear 害怕、Joy 高兴、Anger 生气、Sadness 伤心
   * 自定义接口：允许服务端发送 HEX 颜色代码直接覆盖当前颜色。
 * 表情渲染 (Expression Rendering)：
   * 机制：眼睛几何形态 (Eye Geometry) 和 嘴巴贝塞尔曲线 (Mouth Bezier) ，实时参数驱动（眼睑开合、嘴角弧度）。
   * 预设库（详见附录）：
Neutral 日常/待机、Anxiety 焦虑、Envy 羡慕、Embarrassment 耻辱/尴尬、Ennui 厌倦/无聊、Disgust 恶心、Fear 害怕、Joy 高兴、Anger 生气、Sadness 伤心
* 动作渲染 (Action Rendering)：
   * 机制：通过时间与数学函数让表情动起来，实时参数驱动（眼睑开合、嘴角弧度）。
   * 预设库（详见附录）：
Neutral 日常/待机、Anxiety 焦虑、Envy 羡慕、Embarrassment 耻辱/尴尬、Ennui 厌倦/无聊、Disgust 恶心、Fear 害怕、Joy 高兴、Anger 生气、Sadness 伤心
   * 待机状态 (Standby)：
     * 触发条件：连接正常但无数据输入超过 5 秒。
     * 表现：无嘴巴，轻微的呼吸动效（缩放/透明度变化），偶尔自动眨眼。
   * 思考状态 (Thinking)：
     * 触发条件：交互后的思考间隙。
   * 离线状态 (Offline)：
     * 触发条件：Socket 断开。
     * 表现：颜色变灰，表情闭眼，显示“Zzz”。

**1.3 通信与状态管理**
 * 长连接 (Long-lived Connection)：
   * 优先推荐 UDP（低延迟，丢包不重发，适合实时动画）。
   * 心跳机制：每 30s 发送一次心跳包保活。
 * 数据平滑 (Smoothing)：
   * 在接收端实现 Lerp (线性插值)，避免动画跳变。

## 2. 服务端需求 (Server)

**2.1 核心功能**
 * OpenClaw 接入：
   * 插件方式接入，与前端规定接口细节，并让大模型实现入参。
 * 触发器/拦截层 (Trigger Point - Before Action)：
   * 所有数据发送前必须经过此层，并发送对应参数驱动face表达。

**2.2 异步推送**
 * 采用异步 IO，确保不会阻塞 OpenClaw 的原始数据流。

## 3. 接口定义 (Interface Protocol)
建议使用紧凑的 JSON 格式。
3.1 基础控制帧 (每帧发送，高频)【由于每帧均需调用大模型，极为消耗token，待定】
3.2 情绪控制帧 (触发式)
3.3 表情控制帧 (触发式)
3.4 动作控制帧 (触发式)

## 附录（情绪、表情与动作参数细节）
ClawFace完全由程序生成且只有眼睛和嘴巴，我们将通过颜色 (Color)、眼睛几何形态 (Eye Geometry) 和 嘴巴贝塞尔曲线 (Mouth Bezier) 来抽象表达这些情绪。

**1. 基础参数定义 (Parameter Legend)**

为了方便编写代码，我暂定义一套标准化的参数模型 (0.0 - 1.0 为基准)：
 * Color (Hex): 主色调（建议配合 0x88 或 0xAA 的 Alpha 值实现通透感）。
 * Eyes (双眼):
   * scale_y: 眼睛张开度 (0.0 闭眼, 1.0 正常, 1.5 瞪大)。
   * tilt: 旋转角度 (正值=外眼角向上/开心，负值=外眼角向下/八字眼/悲伤)。
   * squint: 挤眼程度 (切削圆形的顶部或底部，用于表达愤怒或怀疑)。
 * Mouth (嘴巴 - 贝塞尔曲线):
   * curve: 弧度 (-1.0 悲伤/倒U型, 0.0 直线, 1.0 开心/U型)。
   * width: 嘴巴宽度 (0.3 樱桃小嘴, 1.0 大嘴)。
   * open: 张开程度 (0.0 闭合线条, 1.0 张大嘴)。

**2. 情绪预设详表 (Emotion Presets)**

😄 JOY (高兴) 
 * 视觉特征: 明亮、开放、典型的笑脸。
 * 配色: #FFDD33 (明黄) + 光晕 #FFFFCC
 * 眼睛:
   * scale_y: 1.0 (正常)
   * tilt: 0.0 (端正)
 * 嘴巴:
   * curve: 1.0 (大大的 U 型)
   * width: 0.8
   * open: 0.5 (露齿笑的感觉)
😬 ANXIETY (焦虑) 
 * 视觉特征: 眼睛瞪大、瞳孔震颤、嘴巴紧绷或颤抖。
 * 配色: #FF7700 (橙色) + 光晕 #FFAA55
 * 眼睛:
   * scale_y: 1.3 (惊恐地瞪大)
   * tilt: 0.0
   * 特效: 叠加高频微幅随机抖动 (Jitter)。
 * 嘴巴:
   * curve: -0.2 (微微扭曲的波浪线)
   * width: 1.0 (咧得很宽)
   * open: 0.1 (咬紧牙关)
🥺 ENVY (羡慕) 
 * 视觉特征: 眼睛巨大水汪汪、嘴巴小巧。
 * 配色: #00CCCC (青色/Teal) + 光晕 #AAFFFF
 * 眼睛:
   * scale_y: 1.4 (极度放大)
   * tilt: 5.0 (微微上扬)
   * 特效: 瞳孔高光增强 (Starry eyes)。
 * 嘴巴:
   * curve: 0.3 (微笑)
   * width: 0.3 (很小)
   * open: 0.2 (微张，像在发出“哇”的声音)
😳 EMBARRASSMENT (耻辱/害羞) 
 * 视觉特征: 躲闪、想要缩成一团。
 * 配色: #FF6699 (粉红) + 光晕 #FFBBDD
 * 眼睛:
   * scale_y: 0.8 (微眯)
   * tilt: -10.0 (下垂，不敢直视)
   * 特效: 眼睛位置可以整体向下偏移。
 * 嘴巴:
   * curve: -0.1 (抿嘴)
   * width: 0.2 (几乎看不见)
   * open: 0.0
😒 ENNUI (厌倦) 
 * 视觉特征: 没精打采、眼皮耷拉、生无可恋。
 * 配色: #444499 (靛蓝/深紫) + 光晕 #7777AA
 * 眼睛:
   * scale_y: 0.4 (半睁半闭，死鱼眼)
   * tilt: 0.0
 * 嘴巴:
   * curve: 0.0 (完全的直线)
   * width: 0.5
   * open: 0.0
   * 特效: 眨眼速度极慢 (Slow Blink)。
🤢 DISGUST (恶心) 
 * 视觉特征: 翻白眼、撇嘴、不对称。
 * 配色: #66CC33 (草绿) + 光晕 #AAEE88
 * 眼睛:
   * scale_y: 0.7 (眯着看脏东西)
   * tilt: 5.0 (眼角上挑)
   * squint: 0.5 (下眼睑向上挤压)
 * 嘴巴:
   * curve: -0.8 (倒U型)
   * width: 0.6
   * open: 0.2 (嘴角不对称，这需要贝塞尔控制点左右y轴不同)
😱 FEAR (害怕) 
 * 视觉特征: 瘦高惊恐的眼睛、波浪嘴。
 * 配色: #9966CC (亮紫) + 光晕 #CCAAEE
 * 眼睛:
   * scale_y: 1.2 (且横向变窄，瘦长椭圆)
   * tilt: 15.0 (内八字，眉头紧锁的效果)
 * 嘴巴:
   * curve: -0.5 (悲伤弧度)
   * width: 0.4 (缩紧)
   * open: 0.8 (尖叫的O型嘴)
😡 ANGER (生气) 
 * 视觉特征: 倒三角眼、方形嘴。
 * 配色: #DD2222 (正红) + 光晕 #FF5555
 * 眼睛:
   * scale_y: 0.8
   * tilt: 20.0 (极度倒八字，剑眉怒目)
   * squint: Top_Cut (削平圆形上半部，模拟皱眉)
 * 嘴巴:
   * curve: -0.8 (愤怒的下撇)
   * width: 0.9 (咆哮)
   * open: 0.6 (露出牙齿的矩形感)
😢 SADNESS (伤心) 
 * 视觉特征: 整体下垂。
 * 配色: #3366CC (忧郁蓝) + 光晕 #88AAEE
 * 眼睛:
   * scale_y: 0.9
   * tilt: -20.0 (极度八字眉，外眼角下垂)
 * 嘴巴:
   * curve: -1.0 (标准的倒U型)
   * width: 0.6
   * open: 0.1 (微微张开叹气)

**3. 动态效果补充(高级)**

只有静态参数是不够“生动”的，需在代码中为特定情绪加入程序化噪音 (Procedural Noise)，通过数学函数（如正弦波、随机抖动）让静态图形“活”过来。
核心参数示例
1. 活力组 (High Energy)
😄 JOY (高兴) - 乐乐
 * 动作隐喻: "悬浮的气球" (Balloon Float)
 * 噪音逻辑: 缓慢、有节奏的上下起伏。
 * 算法:
   // 模拟呼吸般的轻盈悬浮
val bobbing = Math.sin(t * 2.0) * 10.0 // 频率2Hz，幅度10px
offset_y += bobbing
// 偶尔快速眨眼（双眨眼）
if (random() < 0.01) triggerDoubleBlink()

🥺 ENVY (羡慕) - 慕慕
 * 动作隐喻: "向光生长的花" (Leaning In)
 * 噪音逻辑: 身体整体微微前倾（放大）并左右轻微摇摆，渴望地注视。
 * 算法:
   // 渴望的摇摆
val sway = Math.sin(t * 1.5) * 5.0 // 左右摇摆
offset_x += sway
// 呼吸式放大（心跳感）
val pulse = 1.0 + (Math.sin(t * 3.0) * 0.05) // 缩放范围 0.95 - 1.05
scale_factor *= pulse

2. 焦虑组 (High Frequency / Unstable)
😬 ANXIETY (焦虑) - 焦焦
 * 动作隐喻: "高频震颤" (Jitter/Vibrate)
 * 噪音逻辑: 极快、小幅度的随机位移，像电流流过。
 * 算法:
   // 神经质抖动 (Random Noise)
val jitterX = (Math.random() - 0.5) * 6.0 // -3px 到 +3px
val jitterY = (Math.random() - 0.5) * 6.0
offset_x += jitterX
offset_y += jitterY
// 瞳孔高频微颤 (Saccade)
pupil_offset_x += (Math.random() - 0.5) * 2.0

😱 FEAR (害怕) - 怕怕
 * 动作隐喻: "寒颤" (Shiver)
 * 噪音逻辑: 间歇性的颤抖。平时静止，突然一阵快速抖动。
 * 算法:
   // 叠加正弦波模拟发抖 (只有在特定周期内触发)
val shiverCycle = Math.sin(t * 0.5) // 慢周期
if (shiverCycle > 0.8) { // 只在周期波峰时发抖
    offset_x += Math.sin(t * 50.0) * 3.0 // 极高频，小幅度
}

😳 EMBARRASSMENT (耻辱) - 尬尬
 * 动作隐喻: "甚至想钻地缝" (Turtle Shell)
 * 噪音逻辑: 试图向下缩，然后被弹回，再向下缩。
 * 算法:
   // 缓慢下沉，快速回弹 (Sawtooth wave-ish)
// 这里用 Cos 模拟想藏起来的动作
val hide = Math.abs(Math.sin(t * 0.8)) * 15.0 
offset_y += hide // 总是正值，即向下偏移
scale_x *= 1.02 // 稍微变宽（压扁感）
scale_y *= 0.98

3. 低能耗组 (Low Energy / Heavy)
😒 ENNUI (厌倦) - 丧丧
 * 动作隐喻: "液体/融化" (Melting)
 * 噪音逻辑: 极慢的流动感，像果冻一样。眨眼慢动作。
 * 算法:
   // 极慢的垂直挤压
val melt = Math.sin(t * 0.5) * 0.05 
scale_y += melt // 极其缓慢地变扁再复原
// 这里的关键是眨眼逻辑：
// 普通眨眼耗时200ms，Ennui眨眼耗时 1500ms (半睁半闭状态维持很久)

😢 SADNESS (伤心) - 忧忧
 * 动作隐喻: "沉重的水滴" (Drooping)
 * 噪音逻辑: 整体重心下移，甚至有点拖不动的感觉。
 * 算法:
   // 叹气 (Sighing)
val sigh = Math.sin(t * 0.3) 
if (sigh > 0.8) {
   // 这是一个长叹气周期：先轻微向上吸气，然后重重落下
   offset_y += (sigh - 0.8) * 20.0 
}
// 左右轻微晃动，像不倒翁
rotation = Math.sin(t * 1.0) * 2.0 // +/- 2度倾斜

4. 攻击性组 (Rigid / Intense)
😡 ANGER (生气) - 怒怒
 * 动作隐喻: "沸腾的水壶" (Boiling)
 * 噪音逻辑: 刚性震动。不像 Anxiety 那样随机，而是像机器故障一样的顿挫感。
 * 算法:
   // 怒气积攒 (膨胀)
val buildUp = Math.abs(Math.sin(t * 2.0)) * 0.1
scale_x = 1.0 + buildUp
scale_y = 1.0 + buildUp

// 偶尔的猛烈震动 (Thud)
if (Math.random() < 0.02) {
    offset_y -= 5.0 // 突然向上跳一下（被气炸了）
    offset_x += (Math.random() - 0.5) * 10.0
}

🤢 DISGUST (恶心) - 厌厌
 * 动作隐喻: "抗拒的后退" (Recoil)
 * 噪音逻辑: 缓慢的左右摇头（表示拒绝/嫌弃），身体后倾。
 * 算法:
   // 审判式的摇头 (Judgmental Head Shake)
val headShake = Math.sin(t * 0.5) * 5.0 // 很慢的左右摇头
offset_x += headShake
rotation = headShake * -0.5 // 头部配合反向轻微旋转

