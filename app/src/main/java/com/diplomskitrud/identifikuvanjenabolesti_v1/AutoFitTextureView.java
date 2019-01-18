package com.diplomskitrud.identifikuvanjenabolesti_v1;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

public class AutoFitTextureView extends TextureView {

    private int mRatioWidth =0;
    private int getRatioHeight = 0;

    public AutoFitTextureView(Context context){
        this(context , null);
    }
    public AutoFitTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setAspectRatio(int width , int height){
        if(width < 0 || height < 0){
            throw new IllegalArgumentException("Size cannon be negative ");
        }
        mRatioWidth = width;
        getRatioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if(0 == mRatioWidth || 0 == getRatioHeight){
            setMeasuredDimension(width,height);
        }else{
            if(width < height * mRatioWidth / getRatioHeight){
                setMeasuredDimension(width,width * getRatioHeight / mRatioWidth);
            }else{
                setMeasuredDimension(height * mRatioWidth / getRatioHeight , height);
            }
        }
    }
}
