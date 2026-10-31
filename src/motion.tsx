// Motion and illustration kit. Everything that loops runs on the native driver (transform/opacity only), stops while
// its screen is covered, and is skipped entirely when the phone's "Remove animations" setting is on.
import React, { createContext, useContext, useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, Animated, Easing, StyleProp, StyleSheet, useWindowDimensions, View, ViewStyle } from 'react-native';
import Svg, { Circle, Defs, LinearGradient, Path, Stop } from 'react-native-svg';
import { Txt, Variant } from './ui';

let reduceMotion = false;
AccessibilityInfo.isReduceMotionEnabled().then(v => { reduceMotion = v; });
AccessibilityInfo.addEventListener('reduceMotionChanged', v => { reduceMotion = v; });

/** False while another screen covers this one: ambient loops pause so hidden screens cost nothing. */
export const Awake = createContext(true);
export const useAwake = () => useContext(Awake) && !reduceMotion;

export const easeOut = Easing.out(Easing.cubic);

/** 0→1 forever (native driver). [yoyo] eases back and forth; otherwise it restarts linearly, for drift and rising. [delay] staggers siblings. */
export function useLoop(duration: number, yoyo = true, delay = 0) {
  const v = useRef(new Animated.Value(0)).current;
  const awake = useAwake();
  useEffect(() => {
    if (!awake) return;
    const step = (to: number) =>
      Animated.timing(v, { toValue: to, duration, easing: yoyo ? Easing.inOut(Easing.sin) : Easing.linear, useNativeDriver: true });
    const loop = Animated.loop(yoyo ? Animated.sequence([step(1), step(0)]) : step(1));
    const run = delay ? Animated.sequence([Animated.delay(delay), loop]) : loop;
    run.start();
    return () => run.stop();
  }, [awake, v, duration, yoyo, delay]);
  return v;
}

/** Fades and slides its children up once, on mount. Stagger siblings with [delay]. */
export function Reveal({ children, delay = 0, from = 18, style }: { children: React.ReactNode; delay?: number; from?: number; style?: StyleProp<ViewStyle> }) {
  const p = useRef(new Animated.Value(reduceMotion ? 1 : 0)).current;
  useEffect(() => {
    Animated.timing(p, { toValue: 1, duration: 460, delay, easing: easeOut, useNativeDriver: true }).start();
  }, [p]); // eslint-disable-line react-hooks/exhaustive-deps -- delay only matters on mount
  return (
    <Animated.View style={[style, { opacity: p, transform: [{ translateY: p.interpolate({ inputRange: [0, 1], outputRange: [from, 0] }) }] }]}>
      {children}
    </Animated.View>
  );
}

/** Stagger delay for the i-th item of a list; items past the first screenful just appear. */
export const stagger = (i: number, step = 55) => (i < 8 ? i * step : 0);

/** Springs from small to full size on mount, e.g. labels landing on a photo. */
export function Pop({ children, delay = 0, style }: { children: React.ReactNode; delay?: number; style?: StyleProp<ViewStyle> }) {
  const p = useRef(new Animated.Value(reduceMotion ? 1 : 0)).current;
  useEffect(() => {
    Animated.sequence([Animated.delay(delay), Animated.spring(p, { toValue: 1, speed: 14, bounciness: 10, useNativeDriver: true })]).start();
  }, [p]); // eslint-disable-line react-hooks/exhaustive-deps
  return <Animated.View style={[style, { opacity: p.interpolate({ inputRange: [0, 0.4], outputRange: [0, 1], extrapolate: 'clamp' }), transform: [{ scale: p }] }]}>{children}</Animated.View>;
}

/** A number that counts up to [value] (ease-out, ~0.8 s). Only this text re-renders while it runs. */
export function CountUp({ value, format, v = 'title', color, style }: { value: number; format: (n: number) => string; v?: Variant; color?: string; style?: object }) {
  const [n, setN] = useState(reduceMotion ? value : 0);
  useEffect(() => {
    if (reduceMotion) return setN(value);
    const start = Date.now();
    let raf = 0;
    const tick = () => {
      const k = Math.min(1, (Date.now() - start) / 800);
      setN(value * (1 - (1 - k) ** 3));
      if (k < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [value]);
  return <Txt v={v} color={color} style={style}>{format(n)}</Txt>;
}

const AnimatedCircle = Animated.createAnimatedComponent(Circle);

/** Circular gauge that sweeps to [value] (0..1) with [children] in the middle. */
export function Ring({ value, color, track, size = 64, stroke = 7, children }: { value: number; color: string; track: string; size?: number; stroke?: number; children?: React.ReactNode }) {
  const p = useRef(new Animated.Value(reduceMotion ? value : 0)).current;
  useEffect(() => {
    // SVG props can't use the native driver; this is a single 0.9 s sweep.
    Animated.timing(p, { toValue: value, duration: 900, delay: 150, easing: easeOut, useNativeDriver: false }).start();
  }, [p, value]);
  const r = (size - stroke) / 2;
  const len = 2 * Math.PI * r;
  return (
    <View style={{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }}>
      <Svg width={size} height={size} style={[StyleSheet.absoluteFill, { transform: [{ rotate: '-90deg' }] }]}>
        <Circle cx={size / 2} cy={size / 2} r={r} stroke={track} strokeWidth={stroke} fill="none" />
        <AnimatedCircle
          cx={size / 2} cy={size / 2} r={r} stroke={color} strokeWidth={stroke} fill="none" strokeLinecap="round"
          strokeDasharray={[len, len]} strokeDashoffset={p.interpolate({ inputRange: [0, 1], outputRange: [len, 0] })}
        />
      </Svg>
      {children}
    </View>
  );
}

/** Little sparks flying out of a point once, for a confident match. Place it absolutely over the thing it celebrates. */
export function Burst({ color, count = 8, radius = 34 }: { color: string; count?: number; radius?: number }) {
  const p = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    if (reduceMotion) return;
    Animated.timing(p, { toValue: 1, duration: 750, delay: 250, easing: easeOut, useNativeDriver: true }).start();
  }, [p]);
  return (
    <View pointerEvents="none" style={{ position: 'absolute', width: 0, height: 0 }}>
      {Array.from({ length: count }, (_, i) => {
        const a = (i / count) * Math.PI * 2;
        const size = i % 2 ? 5 : 7;
        return (
          <Animated.View
            key={i}
            style={{
              position: 'absolute', left: -size / 2, top: -size / 2, width: size, height: size, borderRadius: size, backgroundColor: color,
              opacity: p.interpolate({ inputRange: [0, 0.15, 1], outputRange: [0, 1, 0] }),
              transform: [
                { translateX: p.interpolate({ inputRange: [0, 1], outputRange: [0, Math.cos(a) * radius] }) },
                { translateY: p.interpolate({ inputRange: [0, 1], outputRange: [0, Math.sin(a) * radius] }) },
                { scale: p.interpolate({ inputRange: [0, 1], outputRange: [1, 0.3] }) },
              ],
            }}
          />
        );
      })}
    </View>
  );
}

/** Two layered sine waves drifting sideways along the bottom of their parent. Drawn one period wider than the screen and slid by exactly one period, so the loop is seamless. */
export function Waves({ color = '#FFFFFF', height = 56, period = 140 }: { color?: string; height?: number; period?: number }) {
  const { width } = useWindowDimensions();
  const halves = Math.ceil((width + period) / (period / 2)) + 1;
  const path = (amp: number, mid: number) =>
    `M0 ${mid} q ${period / 4} ${-amp} ${period / 2} 0` + ` t ${period / 2} 0`.repeat(halves) + ` V ${height} H 0 Z`;
  const back = useLoop(9000, false);
  const front = useLoop(6000, false);
  const layer = (v: Animated.Value, d: string, opacity: number, reverse?: boolean) => (
    <Animated.View style={{ position: 'absolute', left: 0, bottom: 0, transform: [{ translateX: v.interpolate({ inputRange: [0, 1], outputRange: reverse ? [-period, 0] : [0, -period] }) }] }}>
      <Svg width={width + period * 2} height={height}><Path d={d} fill={color} opacity={opacity} /></Svg>
    </Animated.View>
  );
  return (
    <View pointerEvents="none" style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height, overflow: 'hidden' }}>
      {layer(back, path(7, height * 0.42), 0.1, true)}
      {layer(front, path(9, height * 0.6), 0.14)}
    </View>
  );
}

/** A few bubbles rising and fading, in a [height]-tall column of their parent. */
export function Bubbles({ color = '#FFFFFF', height = 140, spots = [0.62, 0.74, 0.86, 0.7] }: { color?: string; height?: number; spots?: number[] }) {
  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      {spots.map((x, i) => <Bubble key={i} x={x} size={6 + (i % 3) * 4} duration={3200 + i * 900} delay={i * 700} height={height} color={color} />)}
    </View>
  );
}

function Bubble({ x, size, duration, delay, height, color }: { x: number; size: number; duration: number; delay: number; height: number; color: string }) {
  const v = useLoop(duration, false, delay);
  return (
    <Animated.View
      style={{
        position: 'absolute', left: `${x * 100}%`, bottom: 0, width: size, height: size, borderRadius: size, borderWidth: 1.5, borderColor: color,
        opacity: v.interpolate({ inputRange: [0, 0.2, 0.8, 1], outputRange: [0, 0.55, 0.35, 0] }),
        transform: [
          { translateY: v.interpolate({ inputRange: [0, 1], outputRange: [0, -height] }) },
          { translateX: v.interpolate({ inputRange: [0, 0.25, 0.5, 0.75, 1], outputRange: [0, 4, 0, -4, 0] }) },
        ],
      }}
    />
  );
}

/** Gently bobs and tilts its children, like something floating in water. */
export function Float({ children, range = 6, tilt = 3, duration = 2200, style }: { children: React.ReactNode; range?: number; tilt?: number; duration?: number; style?: StyleProp<ViewStyle> }) {
  const v = useLoop(duration);
  return (
    <Animated.View
      style={[style, {
        transform: [
          { translateY: v.interpolate({ inputRange: [0, 1], outputRange: [-range, range] }) },
          { rotate: v.interpolate({ inputRange: [0, 1], outputRange: [`${-tilt}deg`, `${tilt}deg`] }) },
        ],
      }]}>
      {children}
    </Animated.View>
  );
}

/** Flat illustrated fish (brand teal with a marigold band), for empty states and headers. */
export function FishArt({ width = 150, body = '#0E6E7E', band = '#F2A541', eye = '#0B1020' }: { width?: number; body?: string; band?: string; eye?: string }) {
  return (
    <Svg width={width} height={width * (80 / 120)} viewBox="0 0 120 80">
      <Defs>
        <LinearGradient id="fishBody" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor={body} stopOpacity={1} />
          <Stop offset="1" stopColor={body} stopOpacity={0.72} />
        </LinearGradient>
      </Defs>
      <Path d="M44,19C52,6 67,5 76,16Z" fill={body} opacity={0.8} />
      <Path d="M58,58C62,68 70,70 74,64Z" fill={body} opacity={0.8} />
      <Path d="M90,40L114,18C118,30 118,50 114,62Z" fill={body} opacity={0.85} />
      <Path d="M8,40C26,12 66,6 94,40C66,74 26,68 8,40Z" fill="url(#fishBody)" />
      <Path d="M50,17C57,30 57,50 50,63L60,64C66,50 66,30 60,16Z" fill={band} opacity={0.9} />
      <Path d="M18,46C36,60 66,60 86,46C66,54 36,54 18,46Z" fill="#FFFFFF" opacity={0.28} />
      <Path d="M38,27C43,35 43,45 38,53" stroke="#FFFFFF" strokeOpacity={0.5} strokeWidth={2} strokeLinecap="round" fill="none" />
      <Circle cx={25} cy={36} r={5.5} fill="#FFFFFF" />
      <Circle cx={26} cy={36} r={2.8} fill={eye} />
    </Svg>
  );
}
