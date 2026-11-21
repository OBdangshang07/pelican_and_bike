package com.dsdg.pelicanandbike.pelican.client;

import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Targets are in Blockbench model units in the bicycle chassis frame. */
final class PelicanContact {
    static Vector3f pedal(int side,float phase){
        double angle=phase+(side<0?Math.PI:0);
        return new Vector3f(side*2.06f,4.96f-2.72f*(float)Math.cos(angle)+.095f,.98f+2.72f*(float)Math.sin(angle));
    }
    static Vector3f grip(int side,float steer,float brake){
        var palm=new Vector3f(side*4.755f,15.605f,-6.35f);
        var lever=new Vector3f(side*.755f,-.115f,-.30f).rotateY(side*brake*.34f).add(side*3.54f,15.25f,-6.78f);
        return palm.lerp(lever,brake).sub(0,15,-5.8f).rotateY(-steer).add(0,15,-5.8f);
    }
    record Wing(Quaternionf shoulder,Quaternionf elbow) {}
    static Wing reach(int side,Vector3f target,float lift,float aft){
        var shoulder=new Vector3f(side*2.65f,9.4f+lift,-2.1f+aft);
        var upper=new Vector3f(side, .15f,5.1f);
        var lower=new Vector3f(side*.45f,-.17f,-5.2f);
        var d=new Vector3f(target).sub(shoulder);
        float length=d.length();
        var axis=new Vector3f(d).normalize();
        float along=(upper.lengthSquared()-lower.lengthSquared()+length*length)/(2*length);
        var outward=new Vector3f(side*.85f,-.22f,.6f);
        outward.fma(-outward.dot(axis),axis).normalize();
        var joint=axis.mul(along).fma((float)Math.sqrt(Math.max(0,upper.lengthSquared()-along*along)),outward);
        var first=new Quaternionf().rotationTo(upper,joint);
        var second=new Quaternionf().rotationTo(lower,first.transformInverse(new Vector3f(d).sub(joint)));
        return new Wing(first,second);
    }
    static Vector3f joint(Vector3f hip,Vector3f foot,int side){
        var axis=new Vector3f(foot).sub(hip);float distance=Math.max(.001f,axis.length());axis.div(distance);
        float upper=6.15f,lower=6.05f;
        float along=Math.min(upper,(upper*upper-lower*lower+distance*distance)/(2*distance));
        var pole=new Vector3f(side*.16f,0,1);pole.fma(-pole.dot(axis),axis).normalize();
        return new Vector3f(hip).fma(along,axis).fma((float)Math.sqrt(Math.max(0,upper*upper-along*along)),pole);
    }
    static float smooth(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
}
