package com.asha.vrlib;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.asha.vrlib.common.Fps;
import com.asha.vrlib.common.MDGLHandler;
import com.asha.vrlib.plugins.MDAbsLinePipe;
import com.asha.vrlib.plugins.MDAbsPlugin;
import com.asha.vrlib.plugins.MDBarrelDistortionLinePipe;
import com.asha.vrlib.plugins.MDPluginManager;
import com.asha.vrlib.strategy.display.DisplayModeManager;
import com.asha.vrlib.strategy.projection.ProjectionModeManager;

import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static com.asha.vrlib.common.GLUtil.glCheck;

/**
 * Created by hzqiujiadi on 16/1/22.
 * hzqiujiadi ashqalcn@gmail.com
 *
 * @see Builder
 * @see #with(Context)
 */
public class MD360Renderer implements GLSurfaceView.Renderer {

	private static final String TAG = "MD360Renderer";
	private DisplayModeManager mDisplayModeManager;
	private ProjectionModeManager mProjectionModeManager;
	private MDPluginManager mPluginManager;
	private MDAbsLinePipe mMainLinePipe;
	private MDGLHandler mGLHandler;
	private Fps mFps = new Fps();
	private int mWidth;
	private int mHeight;
	private float  distance_mm;
	private float x_view_size_mm;
	private float y_view_size_mm;
	private float scaling_factor;
	private int distance;

	// private MDBarrelDistortionPlugin mBarrelDistortionPlugin;

	// final
	private final Context mContext;

	private MD360Renderer(Builder params){
		mContext = params.context;
		mDisplayModeManager = params.displayModeManager;
		mProjectionModeManager = params.projectionModeManager;
		mPluginManager = params.pluginManager;
		mGLHandler = params.glHandler;

		mMainLinePipe = new MDBarrelDistortionLinePipe(mDisplayModeManager);
		scaling_factor = mDisplayModeManager.getBarrelDistortionConfig().getScale();
		distance_mm = 0;
	}

	public void setViewSizeMm(float x, float y)
    {
        x_view_size_mm = x;
        y_view_size_mm = y;
        calculateDistance();
    }
	public void setDistance(int d)
    {
        distance_mm = d;
    }
	@Override
	public void onSurfaceCreated(GL10 glUnused, EGLConfig config){
		// set the background clear color to black.
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		
		// use culling to remove back faces.
		GLES20.glEnable(GLES20.GL_CULL_FACE);

		// enable depth testing
		// GLES20.glEnable(GLES20.GL_DEPTH_TEST);
	}

	private void calculateDistance()
    {
        if (x_view_size_mm != 0 && y_view_size_mm != 0  && mHeight != 0 && mWidth != 0)
        {
               // 2*h + distance = lens_distance
               // 4*h + distance = mWidth  where d is the pixel distance between the views,
               // h is half the width of the resulting eye view -> solve for d
               int pixel_lens_distance =  (int)(distance_mm / x_view_size_mm * mWidth);
               distance = (2 * pixel_lens_distance - mWidth);
               if (distance < 0)
               {
                   distance = 0;
               }
        }
    }
	@Override
	public void onSurfaceChanged(GL10 glUnused, int width, int height){
		this.mWidth = width;
		this.mHeight = height;
        calculateDistance();
		mGLHandler.dealMessage();
	}

	@Override
	public void onDrawFrame(GL10 glUnused){
		// gl thread
		// 切换策略
		// 热点拾取
		mGLHandler.dealMessage();

		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
		glCheck("MD360Renderer onDrawFrame begin. ");

		int size = mDisplayModeManager.getVisibleSize();

		int width = (int) ((this.mWidth - distance)* 1.0f / size);
		int height = mHeight;

		// take over
		mMainLinePipe.setup(mContext);
		mMainLinePipe.setDistance(distance);
		mMainLinePipe.takeOver(mWidth,mHeight,size);

		List<MD360Director> directors = mProjectionModeManager.getDirectors();

		// main plugin
		MDAbsPlugin mainPlugin = mProjectionModeManager.getMainPlugin();
		if (mainPlugin != null){
			mainPlugin.setupInGL(mContext);
			mainPlugin.beforeRenderer(this.mWidth, this.mHeight);
		}

		for (MDAbsPlugin plugin : mPluginManager.getPlugins()) {
			plugin.setupInGL(mContext);
			plugin.beforeRenderer(this.mWidth, this.mHeight);
		}

		for (int i = 0; i < size; i++){
			if (i >= directors.size()) break;

			MD360Director director = directors.get(i);
			GLES20.glViewport((width + distance)* i, 0, width, height);
			GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
			GLES20.glScissor((width + distance) * i, 0, width, height);

			if (mainPlugin != null){
				mainPlugin.renderer(i, width, height, director);
			}

			for (MDAbsPlugin plugin : mPluginManager.getPlugins()) {
				plugin.renderer(i, width, height, director);
			}

			GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
		}

		mMainLinePipe.commit(mWidth, mHeight, size);
		glCheck("MD360Renderer onDrawFrame end. ");
		// mFps.step();
	}

	public static Builder with(Context context) {
		Builder builder = new Builder();
		builder.context = context;
		return builder;
	}

	public static class Builder{
		private Context context;
		private DisplayModeManager displayModeManager;
		private ProjectionModeManager projectionModeManager;
		private MDGLHandler glHandler;
		private MDPluginManager pluginManager;

		private Builder() {
		}

		public MD360Renderer build(){
			return new MD360Renderer(this);
		}

		public Builder setGLHandler(MDGLHandler glHandler){
			this.glHandler = glHandler;
			return this;
		}

		public Builder setPluginManager(MDPluginManager pluginManager) {
			this.pluginManager = pluginManager;
			return this;
		}

		public Builder setDisplayModeManager(DisplayModeManager displayModeManager) {
			this.displayModeManager = displayModeManager;
			return this;
		}

		public Builder setProjectionModeManager(ProjectionModeManager projectionModeManager) {
			this.projectionModeManager = projectionModeManager;
			return this;
		}
	}
}
