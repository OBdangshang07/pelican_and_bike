package com.dsdg.pelicanandbike.bicycle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.dsdg.pelicanandbike.bicycle.BicycleDynamics.*;

class BicycleDynamicsTest {
    private static final Ground HARD=new Ground(Surface.HARD,true);
    private static Fall advance(BicycleDynamics b,int mask,Ground ground,int frames) {
        Fall last=Fall.NONE;
        for(int i=0;i<frames;i++){var f=b.step(mask,ground,ground,0,true,DT/3);if(f!=Fall.NONE)last=f;}
        return last;
    }
    @Test void driveFreewheelAndDeferredShifts(){
        var bike=new BicycleDynamics();advance(bike,PEDAL,HARD,420);
        assertTrue(bike.speed>4);advance(bike,0,HARD,90);
        double crank=bike.crank,wheel=bike.rearPhase;int gear=bike.gear;
        bike.shift(1);advance(bike,0,HARD,30);assertEquals(gear,bike.gear);
        assertEquals(crank,bike.crank,1e-8);assertTrue(bike.rearPhase>wheel);
        advance(bike,PEDAL,HARD,2);assertEquals(gear+1,bike.gear);
    }
    private double stop(Surface s){
        var bike=new BicycleDynamics();bike.speed=20/3.6;
        double distance=0;
        for(int i=0;i<700&&bike.speed>.01;i++){advance(bike,FRONT|REAR,new Ground(s,true),1);distance+=bike.speed*DT/3;}
        assertTrue(bike.speed<.02);return distance;
    }
    @Test void brakeDistanceReflectsSurface(){
        double dry=stop(Surface.HARD),wet=stop(Surface.WET),ice=stop(Surface.ICE);
        System.out.printf("20 km/h stopping distance: dry %.3f m, wet %.3f m, ice %.3f m%n",dry,wet,ice);
        assertTrue(dry>=2.5&&dry<=5,"dry "+dry);assertTrue(dry<wet&&wet<ice);
    }
    @Test void modestSlipRecoversButHoldingOverloadedTiresFalls(){
        var bike=new BicycleDynamics();bike.speed=6;Ground ice=new Ground(Surface.ICE,true);
        assertEquals(Fall.NONE,advance(bike,RIGHT|REAR,ice,12));
        assertEquals(Fall.NONE,advance(bike,0,HARD,160));
        assertEquals(0,bike.instability,1e-8);
        bike.speed=8;assertNotEquals(Fall.NONE,advance(bike,RIGHT|REAR,ice,135));
    }
    @Test void levelStopAndMissingFoothold(){
        var bike=new BicycleDynamics();
        assertEquals(Fall.NONE,advance(bike,0,HARD,220));
        Fall f=Fall.NONE;
        for(int i=0;i<100&&f==Fall.NONE;i++)f=bike.step(0,HARD,HARD,0,false,DT/3);
        assertEquals(Fall.LEFT,f);
    }
    @Test void eightMetreTurnAtTwentyKilometresPerHour(){
        var bike=new BicycleDynamics();double want=Math.atan(WHEELBASE/8), yaw=0, distance=0;
        for(int i=0;i<660;i++){
            bike.speed=20/3.6;
            advance(bike,bike.steer<want?RIGHT:0,HARD,1);
            if(i>=180){yaw+=bike.yawRate*DT/3;distance+=bike.speed*DT/3;}
        }
        double radius=distance/yaw,angle=Math.toDegrees(bike.lean);
        System.out.printf("20 km/h circle: radius %.2f m; lean %.2f degrees%n",radius,angle);
        assertEquals(8,radius,1);assertEquals(21,angle,5);
    }
    @Test void gradeChangesCoastingAndStandingHasAnEffortCost(){
        var climb=new BicycleDynamics();var descent=new BicycleDynamics();climb.speed=descent.speed=5;
        for(int i=0;i<90;i++){climb.step(0,HARD,HARD,.10,true,DT/3);descent.step(0,HARD,HARD,-.10,true,DT/3);}
        assertTrue(climb.speed<5&&descent.speed>5);
        advance(climb,STAND|PEDAL,HARD,240);double tired=climb.fatigue;
        advance(climb,0,HARD,240);assertTrue(tired>climb.fatigue);
    }
    @Test void tenStartsAndSupportedStopsRemainUpright(){
        var bike=new BicycleDynamics();
        for(int i=0;i<10;i++){
            assertEquals(Fall.NONE,advance(bike,PEDAL,HARD,180));
            assertTrue(bike.speed>2);
            assertEquals(Fall.NONE,advance(bike,FRONT|REAR,HARD,150));
            assertEquals(0,bike.speed,1e-6);
        }
    }
    @Test void uncoveredFootFallsToEitherSideAndLoadedIceCornersMirror(){
        for(int side:new int[]{-1,1}){
            var stopped=new BicycleDynamics();stopped.lean=side*.12;
            var ice=new Ground(Surface.ICE,true);
            var turning=new BicycleDynamics();turning.speed=6;
            Fall lostFoot=Fall.NONE,slid=Fall.NONE;
            for(int i=0;i<200&&lostFoot==Fall.NONE;i++)lostFoot=stopped.step(0,HARD,HARD,0,false,DT/3);
            for(int i=0;i<200&&slid==Fall.NONE;i++)slid=turning.step((side<0?LEFT:RIGHT)|REAR,ice,ice,0,true,DT/3);
            assertEquals(side<0?Fall.LEFT:Fall.RIGHT,lostFoot);
            assertEquals(side<0?Fall.LEFT:Fall.RIGHT,slid);
        }
    }
}
