package com.dsdg.pelicanandbike.bicycle;

/** Wheel contact model in metres, seconds and radians. Positive steering turns right. */
public final class BicycleDynamics {
    public static final double RADIUS = .37, WHEELBASE = 1.12, DT = .05;
    public static final int PEDAL = 1, LEFT = 2, RIGHT = 4, FRONT = 8, REAR = 16,
        STAND = 32, BACK = 64, FORWARD = 128;
    private static final int[] TEETH = {46, 40, 34, 29, 25, 21, 18, 15, 13, 11};
    public enum Surface {
        HARD(.94,.012), WET(.57,.017), GRAVEL(.45,.044), SOIL(.62,.042), ICE(.095,.011);
        public final double grip, rolling;
        Surface(double grip, double rolling) { this.grip = grip; this.rolling = rolling; }
    }
    public record Ground(Surface surface, boolean contact) {}
    public enum Fall { NONE, LEFT, RIGHT, OVER_BARS }
    public double speed, lean, steer, yawRate, pitch, fork, frontBrake, rearBrake, fatigue;
    public double frontPhase, rearPhase, crank, freehub, cadence, skid, frontSlip, rearSlip;
    public double frontRate, rearRate, rearLift, instability;
    public int supportSide=-1;
    public int gear = 4, selected = 4;
    private double turnDemand, lastBrake, counter, rollRate;
    public void shift(int step) { selected = (int) clamp(selected + Integer.signum(step), 0, 9); }
    public static double clamp(double x, double lo, double hi) { return Math.max(lo, Math.min(x, hi)); }
    private static double approach(double from, double to, double rate) {
        return from + clamp(to - from, -rate, rate);
    }
    public Fall step(int input, Ground front, Ground rear, double slope, boolean foot, double dt) {
        boolean pedal = (input & PEDAL) != 0, stand = (input & STAND) != 0;
        int turn = ((input & RIGHT) == 0 ? 0 : 1) - ((input & LEFT) == 0 ? 0 : 1);
        double shift = ((input & FORWARD) != 0 ? 1 : 0) - ((input & BACK) != 0 ? 1 : 0);
        frontBrake = approach(frontBrake, (input & FRONT) != 0 ? 1 : 0, dt * 2.6);
        rearBrake = approach(rearBrake, (input & REAR) != 0 ? 1 : 0, dt * 3.5);
        if (pedal && gear != selected) gear = selected;
        double ratio = 32.0 / TEETH[gear];
        double rpm = Math.abs(speed) / (2 * Math.PI * RADIUS * ratio) * 60;
        fatigue = clamp(fatigue + dt * (pedal && stand ? .033 : -.022), 0, 1);
        double effort = pedal ? Math.max(0, 1 - Math.pow(rpm / (stand ? 145 : 120), 2)) : 0;
        double torque = (stand ? 4.7 - fatigue * 1.7 : 3.1) * effort / ratio;
        double v = Math.abs(speed);
        double demand = turn * Math.toRadians(37 / (1 + v * v * .045));
        counter += (demand - turnDemand) * Math.min(1, v / 4) * 1.5;
        counter *= Math.exp(-dt * 8);
        turnDemand = demand;
        steer = approach(steer, demand - counter, dt * (v>2 ? 1.1 : 2.8));
        double lateral = speed * speed * Math.tan(steer) / WHEELBASE;
        double frontLoad = clamp(.44 + shift * .13 + lastBrake * .077, .13, 1);
        double rearLoad = 1 - frontLoad;
        double frontGrip = front.contact ? front.surface.grip * 9.81 : 0;
        double rearGrip = rear.contact ? rear.surface.grip * 9.81 : 0;
        double fDemand = 7.6 * frontBrake, rDemand = 4.3 * rearBrake;
        double fBudget = frontGrip * frontLoad, rBudget = rearGrip * rearLoad;
        double fApplied = Math.min(fDemand, fBudget), rApplied = Math.min(rDemand, rBudget);
        double braking = fApplied + rApplied;
        lastBrake += (braking - lastBrake) * Math.min(1, dt * 10);
        frontSlip = front.contact && v > .45 ? clamp((Math.hypot(fDemand, lateral * frontLoad) - fBudget) / Math.max(.4, fBudget),0,1) : 0;
        rearSlip = rear.contact && v > .45 ? clamp((Math.hypot(rDemand, lateral * rearLoad) - rBudget) / Math.max(.4, rBudget),0,1) : 0;
        double drive = Math.min(torque, rBudget);
        double drag = .0042 * v * v + 9.81 * (front.surface.rolling * frontLoad + rear.surface.rolling * rearLoad);
        double force = drive - (front.contact || rear.contact ? 9.81 * Math.sin(slope) : 0);
        speed += force * dt;
        speed = approach(speed, 0, (braking + drag) * dt);
        speed = clamp(speed, -7, 24);
        rearLift = approach(rearLift, front.contact && speed > 2 ? clamp((.44 + shift * .13 + fApplied * .087 - .95) * 5,0,1) : 0, dt * 3);
        yawRate = speed * Math.tan(steer) / WHEELBASE * (1 - .8 * frontSlip);
        double side = Math.signum(Math.abs(steer) > .004 ? steer : lean);
        skid = approach(skid, side * rearSlip * Math.min(v / 7, 1) * .48, dt * 1.8);
        if (!front.contact && !rear.contact) yawRate = 0;
        double target = Math.atan2(lateral, 9.81);
        if (v < .8) target = foot ? Math.copySign(.105,supportSide) : (lean <= 0 ? -.9 : .9);
        if(stand && pedal && v>1)target+=Math.sin(crank)*.041*(1-fatigue*.45);
        instability = clamp(instability + dt * (v > 1.3 && Math.abs(lateral) > .9 ?
            2.15 * frontSlip + 1.0 * rearSlip - .68 : -2.1),0,1.6);
        target += side * instability * .55;
        rollRate += (30 * (target - lean) - 8 * rollRate) * dt;
        lean += rollRate * dt;
        pitch += (slope - pitch) * Math.min(1, dt * 8);
        fork = approach(fork, clamp(lastBrake * .011 + Math.max(0,slope) * .08,0,.1), dt * .24);
        frontRate = front.contact ? speed / RADIUS * (1 - frontSlip) : approach(frontRate,0,dt*.55);
        rearRate = rear.contact ? speed / RADIUS * (1 - rearSlip) : approach(rearRate,pedal ? 9 * ratio : 0,dt * (pedal ? 24 : .55));
        frontPhase += frontRate * dt;
        rearPhase += rearRate * dt;
        cadence = approach(cadence, pedal ? Math.max(1.4, Math.min(15,Math.abs(rearRate) / ratio)) : 0, dt * 25);
        crank += cadence * dt;
        freehub += cadence * ratio * dt;
        if(!pedal && turn!=0 && v>2 && Math.abs(lean)>.15 && cadence<.05){
            // Back-pedal to raise the inside platform while the wheel freewheels.
            double flat=turn>0?Math.PI:0;
            double remaining=(crank-flat)%(2*Math.PI);
            if(remaining<0)remaining+=2*Math.PI;
            if(remaining<2*Math.PI-.015){
                double movement=Math.min(remaining,dt*5);
                crank-=movement;freehub-=movement*ratio;
            }
        }
        if (rearLift > .78 && shift > 0) return Fall.OVER_BARS;
        if (Math.abs(lean) > 1.04 || instability > .98 || v < .8 && !foot && Math.abs(lean) > .64)
            return lean < 0 ? Fall.LEFT : Fall.RIGHT;
        return Fall.NONE;
    }
}
