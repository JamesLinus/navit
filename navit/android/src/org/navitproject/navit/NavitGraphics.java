/**
 * Navit, a modular navigation system.
 * Copyright (C) 2005-2008 Navit Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

package org.navitproject.navit;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.util.FloatMath;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.GestureDetector;
import android.view.inputmethod.InputMethodManager;
import android.widget.RelativeLayout;




public class NavitGraphics
{
	private NavitGraphics            parent_graphics;
	private ArrayList<NavitGraphics> overlays          = new ArrayList<NavitGraphics>();
	int                              bitmap_w;
	int                              bitmap_h;
	int                              pos_x;
	int                              pos_y;

	int                              pos_wraparound;
	int                              overlay_disabled;
	float                            trackball_x, trackball_y;
	View                             view;
	RelativeLayout                   relativelayout;
	NavitCamera                      camera;
	Activity                         activity;

	//public NavitManagerThread 		 		manThread;
	private static NavitDrawObjectsPool	 	drawThreadPool;
	
	public static int 						THREAD_NUM = 1;
	public static boolean					draw_in_thread = true;
	
	public float				 			 zoomFactor = 1;


	public static Boolean            in_map            = false;

	// for menu key
	private static long              time_for_long_press               = 300L;
	private static long              interval_for_long_press           = 200L;

	private Handler timer_handler = new Handler();



	public void SetCamera(int use_camera)
	{
		if (use_camera != 0 && camera == null)
		{
			// activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
			camera = new NavitCamera(activity);
			relativelayout.addView(camera);
			relativelayout.bringChildToFront(view);
		}
	}

	protected Rect get_rect()
	{
		Rect ret=new Rect();
		ret.left=pos_x;
		ret.top=pos_y;
		if (pos_wraparound != 0) {
			if (ret.left < 0) {
				ret.left+=parent_graphics.bitmap_w;
			}
			if (ret.top < 0) {
				ret.top+=parent_graphics.bitmap_h;
			}
		}
		ret.right=ret.left+bitmap_w;
		ret.bottom=ret.top+bitmap_h;
		if (pos_wraparound != 0) {
			if (bitmap_w < 0) {
				ret.right=ret.left+bitmap_w+parent_graphics.bitmap_w;
			}
			if (bitmap_h < 0) {
				ret.bottom=ret.top+bitmap_h+parent_graphics.bitmap_h;
			}
		}
		return ret;
	}

	private class NavitView extends View implements Runnable, MenuItem.OnMenuItemClickListener{
		int               touch_mode = NONE;
		float             oldDist    = 0;
		static final int  NONE       = 0;
		static final int  DRAG       = 1;
		static final int  ZOOM       = 2;
		static final int  PRESSED    = 3;



		Method eventGetX = null;
		Method eventGetY = null;
		
		public PointF    mPressedPosition = null;
		
		public NavitView(Context context) {
			super(context);
			try
			{
				eventGetX = android.view.MotionEvent.class.getMethod("getX", int.class);
				eventGetY = android.view.MotionEvent.class.getMethod("getY", int.class);
			}
			catch (Exception e)
			{
				Log.e("NavitGraphics", "Multitouch zoom not supported");
			}
		}

		@Override
		protected void onCreateContextMenu(ContextMenu menu) {
			super.onCreateContextMenu(menu);
			
			menu.setHeaderTitle(Navit._("Position")+"..");
			menu.add(1, 1, NONE, Navit._("Route to here")).setOnMenuItemClickListener(this);
			menu.add(1, 2, NONE, Navit._("Cancel")).setOnMenuItemClickListener(this);
		}

		@Override
		public boolean onMenuItemClick(MenuItem item) {
			switch(item.getItemId()) {
			case 1:
				Message msg = Message.obtain(callback_handler, msg_type.CLB_SET_DISPLAY_DESTINATION.ordinal()
				   , (int)mPressedPosition.x, (int)mPressedPosition.y);
				msg.sendToTarget();
				break;
			}
			return false;
		}

		
		@Override
		protected void onDraw(Canvas canvas)
		{
			super.onDraw(canvas);
			
			
			
			if( overlay_disabled == 0) {

				
				
				if(zoomFactor != 1) {

					Bitmap scaledBitmap = draw_bitmap;

					canvas.save();

					canvas.translate(-1 * (bitmap_w * zoomFactor - bitmap_w) / 2, -1 *(bitmap_h * zoomFactor - bitmap_h) / 2);
					canvas.scale(zoomFactor, zoomFactor);

					Paint paint = new Paint();
					paint.setAntiAlias(true);
					paint.setFilterBitmap(true);
					paint.setDither(true);

					canvas.drawBitmap(scaledBitmap, pos_x, pos_y, paint);

					canvas.restore();


				} else {

					canvas.drawBitmap(draw_bitmap, pos_x, pos_y, null);


				}
			} else {
				canvas.drawBitmap(draw_bitmap, pos_x, pos_y, null);
			}
			
			
			
			if (overlay_disabled == 0)
			{
				//Log.e("NavitGraphics", "view -> onDraw 1");
				// assume we ARE in map view mode!
				in_map = true;

				Object overlays_array[];
				overlays_array = overlays.toArray();
				for (Object overlay : overlays_array)
				{
					//Log.e("NavitGraphics","view -> onDraw 2");

					NavitGraphics overlay_graphics = (NavitGraphics) overlay;
					if (overlay_graphics.overlay_disabled == 0)
					{
						//Log.e("NavitGraphics","view -> onDraw 3");
						Rect r=overlay_graphics.get_rect();
						canvas.drawBitmap(overlay_graphics.draw_bitmap, r.left, r.top, null);
					}
				}
			}
			else
			{
				if (Navit.show_soft_keyboard)
				{
					if (Navit.mgr != null)
					{
						//Log.e("NavitGraphics", "view -> SHOW SoftInput");
						//Log.e("NavitGraphics", "view mgr=" + String.valueOf(Navit.mgr));
						Navit.mgr.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
						Navit.show_soft_keyboard_now_showing = true;
						// clear the variable now, keyboard will stay on screen until backbutton pressed
						Navit.show_soft_keyboard = false;
					}
				}
			}
			
			/*
			// thats a way to check how many free memory is available
			final Runtime runtime = Runtime.getRuntime();
			final long usedMemInMB=(runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
			final long maxHeapSizeInMB=runtime.maxMemory() / 1048576L;
			
			Log.e("Navit", "usedMem: " + usedMemInMB + " MB  maxHeapSize: " + maxHeapSizeInMB + " MB");
			*/
			
		}

		@Override
		protected void onSizeChanged(int w, int h, int oldw, int oldh)
		{
			Log.e("Navit", "NavitGraphics -> onSizeChanged pixels x=" + w + " pixels y=" + h);
			Log.e("Navit", "NavitGraphics -> onSizeChanged density=" + Navit.metrics.density);
			Log.e("Navit", "NavitGraphics -> onSizeChanged scaledDensity="
					+ Navit.metrics.scaledDensity);

			super.onSizeChanged(w, h, oldw, oldh); // was before bitmap/canvas init

			
			
			//each pixel is stored on 4 bytes
			// http://developer.android.com/reference/android/graphics/Bitmap.Config.html
			draw_bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
			draw_canvas = new Canvas(draw_bitmap);
			
			
			//create bitmap to cache the map
			cached_bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
			draw_canvas = new Canvas(draw_bitmap);
			cached_canvas = new Canvas(cached_bitmap);
			
			drawThreadPool.init(draw_canvas, w, h);

			

			bitmap_w = w;
			bitmap_h = h;
			SizeChangedCallback(SizeChangedCallbackID, w, h);
		}

		public void do_longpress_action()
		{
			Log.e("NavitGraphics", "do_longpress_action enter");
			
			activity.openContextMenu(this);
		}

		private int getActionField(String fieldname, Object obj)
		{
			int ret_value = -999;
			try
			{
				java.lang.reflect.Field field = android.view.MotionEvent.class.getField(fieldname);
				try
				{
					ret_value = field.getInt(obj);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
			catch (NoSuchFieldException ex) {}

			return ret_value;
		}
		
		
		
		public final GestureDetector gestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
		    public void onLongPress(MotionEvent e) {
						
			if (in_map && touch_mode == PRESSED)
			{
				do_longpress_action();
				touch_mode = NONE;
			}


		    }
		});
		
		@Override
		public boolean onTouchEvent(MotionEvent event)
		{
			//Log.e("NavitGraphics", "onTouchEvent");
			super.onTouchEvent(event);
			int x = (int) event.getX();
			int y = (int) event.getY();

			int _ACTION_POINTER_UP_ = getActionField("ACTION_POINTER_UP", event);
			int _ACTION_POINTER_DOWN_ = getActionField("ACTION_POINTER_DOWN", event);
			int _ACTION_MASK_ = getActionField("ACTION_MASK", event);

			int switch_value = event.getAction();
			if (_ACTION_MASK_ != -999)
			{
				switch_value = (event.getAction() & _ACTION_MASK_);
			}

			if (switch_value == MotionEvent.ACTION_DOWN)
			{	/* one (first) finger touches the screen */



				touch_mode = PRESSED;
				if (!in_map) {
					ButtonCallback(ButtonCallbackID, 1, 1, x, y); // down
					//manThread.addButtonEvent(ButtonCallbackID, 1, 1, x, y); // down
				}


				mPressedPosition = new PointF(x, y);
				gestureDetector.onTouchEvent(event);

			}
			else if ((switch_value == MotionEvent.ACTION_UP) || (switch_value == _ACTION_POINTER_UP_))
			{ /* one or two finger left the screen */
				Log.e("NavitGraphics", "ACTION_UP");

				if ( touch_mode == DRAG )
				{
					Log.e("NavitGraphics", "onTouch move");


					MotionCallback(MotionCallbackID, x, y);
					ButtonCallback(ButtonCallbackID, 0, 1, x, y);

					//zoomFactor = 1;
					//manThread.addMotionEvent(MotionCallbackID, x, y);

					//manThread.addButtonEvent(ButtonCallbackID, 0, 1, x, y); // up


				}
				else if (touch_mode == ZOOM)
				{
					Log.e("NavitGraphics", "onTouch zoom");

					float newDist = spacing(getFloatValue(event, 0), getFloatValue(event, 1));
					float scale = 0;
					
					float z = 1;
					
					if (newDist > 0f)
					{
						scale = newDist / oldDist;
						zoomFactor = scale;
						viewInvalidate(); // hier kann direkt invalidate aufgerufen werden, da es immer aus dem main-thread ausgefuehrt wird.
						
					}
					

					
					CallbackZoom(scale);
					//Log.e("NavitGraphics", "zoom (get new map)");
					

				}
				else if (touch_mode == PRESSED)
				{
					Log.e("NavitGraphics", "onTouch pressed!!");
					if (in_map) {
						//manThread.addButtonEvent(ButtonCallbackID, 1, 1, x, y); // down	
						ButtonCallback(ButtonCallbackID, 1, 1, x, y); //down
						
						ButtonCallback(ButtonCallbackID, 0, 1, x, y); //up
						//manThread.addButtonEvent(ButtonCallbackID, 0, 1, x, y); // up
					} else {
						
						ButtonCallback(ButtonCallbackID, 0, 1, x, y); //up
					}
					
				}
				touch_mode = NONE;
			}
			else if (switch_value == MotionEvent.ACTION_MOVE)
			{
				Log.e("NavitGraphics", "ACTION_MOVE");

				if (touch_mode == DRAG)
				{
					MotionCallback(MotionCallbackID, x, y);
				}
				else if (touch_mode == ZOOM)
				{
					float newDist = spacing(getFloatValue(event, 0), getFloatValue(event, 1));
					float scale = newDist / oldDist;
					
					if (newDist > 0f) {
						zoomFactor = scale;
						viewInvalidate();
					}
	
				}
				else if (touch_mode == PRESSED)
				{
					Log.e("NavitGraphics", "Start drag mode");
					if ( spacing(mPressedPosition, new PointF(event.getX(),  event.getY()))  > 20f) {
						//manThread.addButtonEvent(ButtonCallbackID, 1, 1, x, y); // down
						ButtonCallback(ButtonCallbackID, 1,1 ,x,y); //down
						touch_mode = DRAG;
					}
				}
			}
			else if (switch_value == _ACTION_POINTER_DOWN_)
			{/* second finger attached to the screen */
				//Log.e("NavitGraphics", "ACTION_POINTER_DOWN");
				oldDist = spacing(getFloatValue(event, 0), getFloatValue(event, 1));
				if (oldDist > 0f)
				{
					touch_mode = ZOOM;
					//Log.e("NavitGraphics", "--> zoom");
				}
			}
			return true;
		}

		private float spacing(PointF a, PointF b)
		{
			float x = a.x - b.x;
			float y = a.y - b.y;
			return FloatMath.sqrt(x * x + y * y);
		}

		private PointF getFloatValue(Object instance, Object argument)
		{
			PointF pos = new PointF(0,0); 
			
			if (eventGetX != null && eventGetY != null)
			{
				try
				{
					Float x = (java.lang.Float) eventGetX.invoke(instance, argument);
					Float y = (java.lang.Float) eventGetY.invoke(instance, argument);
					pos.set(x.floatValue(), y.floatValue());
					
				}
				catch (Exception e){}
			}
			return pos;
		}
		
		@Override
		public boolean onKeyDown(int keyCode, KeyEvent event)
		{
			int i;
			String s = null;
			boolean handled = true;
			i = event.getUnicodeChar();
			//Log.e("NavitGraphics", "onKeyDown " + keyCode + " " + i);
			// Log.e("NavitGraphics","Unicode "+event.getUnicodeChar());
			if (i == 0)
			{
				if (keyCode == android.view.KeyEvent.KEYCODE_DEL)
				{
					s = java.lang.String.valueOf((char) 8);
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_MENU)
				{
					if (!in_map)
					{
						// if last menukeypress is less than 0.2 seconds away then count longpress
						if ((System.currentTimeMillis() - Navit.last_pressed_menu_key) < interval_for_long_press)
						{
							Navit.time_pressed_menu_key = Navit.time_pressed_menu_key
									+ (System.currentTimeMillis() - Navit.last_pressed_menu_key);
							//Log.e("NavitGraphics", "press time=" + Navit.time_pressed_menu_key);

							// on long press let softkeyboard popup
							if (Navit.time_pressed_menu_key > time_for_long_press)
							{
								//Log.e("NavitGraphics", "long press menu key!!");
								Navit.show_soft_keyboard = true;
								Navit.time_pressed_menu_key = 0L;
								// need to draw to get the keyboard showing
								this.postInvalidate();
							}
						}
						else
						{
							Navit.time_pressed_menu_key = 0L;
						}
						Navit.last_pressed_menu_key = System.currentTimeMillis();
						// if in menu view:
						// use as OK (Enter) key
						s = java.lang.String.valueOf((char) 13);
						handled = true;
						// dont use menu key here (use it in onKeyUp)
						return handled;
					}
					else
					{
						// if on map view:
						// volume UP
						//s = java.lang.String.valueOf((char) 1);
						handled = false;
						return handled;
					}
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_SEARCH)
				{
					/* Handle event in Main Activity if map is shown */
					if(in_map)
						return false;
					
					s = java.lang.String.valueOf((char) 19);
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_BACK)
				{
					//Log.e("NavitGraphics", "KEYCODE_BACK down");
					s = java.lang.String.valueOf((char) 27);
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_CALL)
				{
					s = java.lang.String.valueOf((char) 3);
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP)
				{
					if (!in_map)
					{
						// if in menu view:
						// use as UP key
						s = java.lang.String.valueOf((char) 16);
						handled = true;
					}
					else
					{
						// if on map view:
						// volume UP
						//s = java.lang.String.valueOf((char) 21);
						handled = false;
						return handled;
					}
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
				{
					if (!in_map)
					{
						// if in menu view:
						// use as DOWN key
						s = java.lang.String.valueOf((char) 14);
						handled = true;
					}
					else
					{
						// if on map view:
						// volume DOWN
						//s = java.lang.String.valueOf((char) 4);
						handled = false;
						return handled;
					}
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER)
				{
					s = java.lang.String.valueOf((char) 13);
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN)
				{
					s = java.lang.String.valueOf((char) 16);
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT)
				{
					s = java.lang.String.valueOf((char) 2);
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
				{
					s = java.lang.String.valueOf((char) 6);
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP)
				{
					s = java.lang.String.valueOf((char) 14);
				}
			} 
			else if (i == 10)
			{
				s = java.lang.String.valueOf((char) 13);
			}

			if (s != null)
			{
				KeypressCallback(KeypressCallbackID, s);
			}
			return handled;
		}

		@Override
		public boolean onKeyUp(int keyCode, KeyEvent event)
		{
			//Log.e("NavitGraphics", "onKeyUp " + keyCode);

			int i;
			String s = null;
			boolean handled = true;
			i = event.getUnicodeChar();

			if (i == 0)
			{
				if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP)
				{
					if (!in_map)
					{
						//s = java.lang.String.valueOf((char) 16);
						handled = true;
						return handled;
					}
					else
					{
						//s = java.lang.String.valueOf((char) 21);
						handled = false;
						return handled;
					}
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
				{
					if (!in_map)
					{
						//s = java.lang.String.valueOf((char) 14);
						handled = true;
						return handled;
					}
					else
					{
						//s = java.lang.String.valueOf((char) 4);
						handled = false;
						return handled;
					}
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_SEARCH) {
					/* Handle event in Main Activity if map is shown */
					if(in_map)
						return false;
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_BACK)
				{
					if (Navit.show_soft_keyboard_now_showing)
					{
						Navit.show_soft_keyboard_now_showing = false;
					}
					//Log.e("NavitGraphics", "KEYCODE_BACK up");
					//s = java.lang.String.valueOf((char) 27);
					handled = true;
					return handled;
				}
				else if (keyCode == android.view.KeyEvent.KEYCODE_MENU)
				{
					if (!in_map)
					{
						if (Navit.show_soft_keyboard_now_showing)
						{
							// if soft keyboard showing on screen, dont use menu button as select key
						}
						else
						{
							// if in menu view:
							// use as OK (Enter) key
							s = java.lang.String.valueOf((char) 13);
							handled = true;
						}
					}
					else
					{
						// if on map view:
						// volume UP
						//s = java.lang.String.valueOf((char) 1);
						handled = false;
						return handled;
					}
				}
			}
			else if(i!=10)
			{
				s = java.lang.String.valueOf((char) i);
			} 
						
			if (s != null)
			{
				KeypressCallback(KeypressCallbackID, s);
			}
			return handled;

		}

		@Override
		public boolean onKeyMultiple (int keyCode, int count, KeyEvent event)
		{
			String s = null;
			if(keyCode == KeyEvent.KEYCODE_UNKNOWN) {
				s=event.getCharacters();
				KeypressCallback(KeypressCallbackID, s);
				return true;
			}
			return super.onKeyMultiple(keyCode, count, event);
		}
		
		@Override
		public boolean onTrackballEvent(MotionEvent event)
		{
			//Log.e("NavitGraphics", "onTrackball " + event.getAction() + " " + event.getX() + " "
			//		+ event.getY());
			String s = null;
			if (event.getAction() == android.view.MotionEvent.ACTION_DOWN)
			{
				s = java.lang.String.valueOf((char) 13);
			}
			if (event.getAction() == android.view.MotionEvent.ACTION_MOVE)
			{
				trackball_x += event.getX();
				trackball_y += event.getY();
				//Log.e("NavitGraphics", "trackball " + trackball_x + " " + trackball_y);
				if (trackball_x <= -1)
				{
					s = java.lang.String.valueOf((char) 2);
					trackball_x += 1;
				}
				if (trackball_x >= 1)
				{
					s = java.lang.String.valueOf((char) 6);
					trackball_x -= 1;
				}
				if (trackball_y <= -1)
				{
					s = java.lang.String.valueOf((char) 16);
					trackball_y += 1;
				}
				if (trackball_y >= 1)
				{
					s = java.lang.String.valueOf((char) 14);
					trackball_y -= 1;
				}
			}
			if (s != null)
			{
				KeypressCallback(KeypressCallbackID, s);
			}
			return true;
		}
		@Override
		protected void onFocusChanged(boolean gainFocus, int direction,
				Rect previouslyFocusedRect)
		{
			super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
			//Log.e("NavitGraphics", "FocusChange " + gainFocus);
		}

		public void run() {
			if (in_map && touch_mode == PRESSED)
			{
				do_longpress_action();
				touch_mode = NONE;
			}
		}
		
	}
	
	public NavitGraphics(final Activity activity, NavitGraphics parent, int x, int y, int w, int h,
			int alpha, int wraparound, int use_camera)
	{
		if (parent == null)
		{
			drawThreadPool = new NavitDrawObjectsPool(this, THREAD_NUM);
			
			this.activity = activity;			
			view = new NavitView(activity);
			//activity.registerForContextMenu(view);
			view.setClickable(false);
			view.setFocusable(true);
			view.setFocusableInTouchMode(true);
			view.setKeepScreenOn(true);
			relativelayout = new RelativeLayout(activity);
			if (use_camera != 0)
			{
				SetCamera(use_camera);
			}
			relativelayout.addView(view);

			activity.setContentView(relativelayout);
			view.requestFocus();
		}
		else
		{
			draw_bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
			bitmap_w = w;
			bitmap_h = h;
			pos_x = x;

			pos_y = y;

			pos_wraparound = wraparound;
			draw_canvas = new Canvas(draw_bitmap);
			parent.overlays.add(this);
		}
		parent_graphics = parent;

		//manThread = NavitManagerThread.manThread;

	}

	static public enum msg_type {
		CLB_ZOOM_IN, CLB_ZOOM_OUT, CLB_REDRAW, CLB_MOVE, CLB_BUTTON_UP, CLB_BUTTON_DOWN, CLB_SET_DESTINATION
		, CLB_SET_DISPLAY_DESTINATION, CLB_CALL_CMD, CLB_COUNTRY_CHOOSER, CLB_LOAD_MAP, CLB_UNLOAD_MAP, CLB_DELETE_MAP
	};

	static public msg_type[] msg_values = msg_type.values();
	
	public Handler	callback_handler	= new Handler()
		{
			public void handleMessage(Message msg)
			{
				switch (msg_values[msg.what])
				{
				case CLB_ZOOM_IN:
					CallbackMessageChannel(1, "");
					break;
				case CLB_ZOOM_OUT:
					CallbackMessageChannel(2, "");
					break;
				case CLB_MOVE:
					MotionCallback(MotionCallbackID, msg.getData().getInt("x"), msg.getData().getInt("y"));
					
					break;
				case CLB_SET_DESTINATION:
					String lat = Float.toString(msg.getData().getFloat("lat"));
					String lon = Float.toString(msg.getData().getFloat("lon"));
					String q = msg.getData().getString(("q"));
					CallbackMessageChannel(3, lat + "#" + lon + "#" + q);
					break;
				case CLB_SET_DISPLAY_DESTINATION:
					int x = msg.arg1;
					int y = msg.arg2;
					CallbackMessageChannel(4, "" + x + "#" + y);
					break;
				case CLB_CALL_CMD:
					String cmd = msg.getData().getString(("cmd"));
					CallbackMessageChannel(5, cmd);
					break;
				case CLB_BUTTON_UP:
					ButtonCallback(ButtonCallbackID, 0, 1, msg.getData().getInt("x"), msg.getData().getInt("y")); // up
					//manThread.addButtonEvent(ButtonCallbackID, 0, 1, msg.getData().getInt("x"), msg.getData().getInt("y")); // up
					break;
				case CLB_BUTTON_DOWN:
					ButtonCallback(ButtonCallbackID, 1, 1, msg.getData().getInt("x"), msg.getData().getInt("y")); // down
					//manThread.addButtonEvent(ButtonCallbackID, 1, 1, msg.getData().getInt("x"), msg.getData().getInt("y")); // down
					break;
				case CLB_COUNTRY_CHOOSER:
					break;
				case CLB_LOAD_MAP:
					CallbackMessageChannel(6, msg.getData().getString(("title")));
					break;
				case CLB_DELETE_MAP:
					File toDelete = new File( msg.getData().getString(("title")));
					toDelete.delete();
				//fallthrough
				case CLB_UNLOAD_MAP:
					CallbackMessageChannel(7, msg.getData().getString(("title")));
					break;
				}
			}
		};

	public native void SizeChangedCallback(int id, int x, int y);
	public native void KeypressCallback(int id, String s);
	public native int CallbackMessageChannel(int i, String s);
	
	public native int CallbackZoom(float zoomfactor);

	public native void ButtonCallback(int id, int pressed, int button, int x, int y);
	public native void MotionCallback(int id, int x, int y);
	public native String GetDefaultCountry(int id, String s);
	public static native String[][] GetAllCountries();
	public Canvas	draw_canvas;
	public Bitmap	draw_bitmap;
	public Bitmap	cached_bitmap;
	public Canvas	cached_canvas;
	private int		SizeChangedCallbackID, ButtonCallbackID, MotionCallbackID, KeypressCallbackID;
	// private int count;

	public void setSizeChangedCallback(int id)
	{
		SizeChangedCallbackID = id;
	}
	public void setButtonCallback(int id)
	{
		ButtonCallbackID = id;
	}
	public void setMotionCallback(int id)
	{
		MotionCallbackID = id;
		Navit.setMotionCallback(id, this);
	}

	public void setKeypressCallback(int id)
	{
		KeypressCallbackID = id;
		// set callback id also in main intent (for menus)
		Navit.setKeypressCallback(id, this);
	}
	
	private Paint paint = new Paint();
	private Path draw_path = new Path();

	protected void draw_polyline(Paint paint, int c[])
	{
		if(parent_graphics == null && in_map && draw_in_thread)
			drawThreadPool.add_polyline(paint, c);
		else {
			
			int i, ndashes;
			float [] intervals;

			paint.setStrokeWidth(c[0]);
			paint.setARGB(c[1],c[2],c[3],c[4]);
			paint.setStyle(Paint.Style.STROKE);

			
			paint.setStrokeCap(Paint.Cap.ROUND);      

			ndashes=c[5];
			intervals=new float[ndashes+(ndashes%2)];
			for (i = 0; i < ndashes; i++)
				intervals[i]=c[6+i];

			if((ndashes%2)==1)
				intervals[ndashes]=intervals[ndashes-1];

			if(ndashes>0)
				paint.setPathEffect(new android.graphics.DashPathEffect(intervals,0.0f));

			
			draw_path.rewind();
			draw_path.moveTo(c[6+ndashes], c[7+ndashes]);
			for (i = 8+ndashes; i < c.length; i += 2)
			{
				draw_path.lineTo(c[i], c[i + 1]);
			}
			//global_path.close();
			draw_canvas.drawPath(draw_path, paint);
			
			paint.setPathEffect(null);	
			
			
			
		}
	}

	protected void draw_polygon(Paint paint, int c[])
	{
		if(parent_graphics == null && in_map && draw_in_thread)
			drawThreadPool.add_polygon(paint, c);
		else {
			
			paint.setStrokeWidth(c[0]);
			paint.setARGB(c[1],c[2],c[3],c[4]);
			paint.setStyle(Paint.Style.FILL);
			//paint.setAntiAlias(true);
			//paint.setStrokeWidth(0);
			draw_path.rewind();
			draw_path.moveTo(c[5], c[6]);
			for (int i = 7; i < c.length; i += 2)
			{
				draw_path.lineTo(c[i], c[i + 1]);
			}
			//global_path.close();
			draw_canvas.drawPath(draw_path, paint);
			
			
		}
	}
	

	//used for background color
	protected void draw_rectangle(Paint paint, int x, int y, int w, int h)
	{
		if(THREAD_NUM == 0) {
			draw_in_thread = false;	
		} else {
			draw_in_thread = true;	
		}

		if(parent_graphics == null && in_map && draw_in_thread) {
			drawThreadPool.setThread_n(THREAD_NUM);
			
			//add if only 1 thread is active
			
			//drawThreadPool.add_rectangle(paint, x, y, w, h);

			Rect r = new Rect(x, y, x + w, y + h);
			paint.setStyle(Paint.Style.FILL);
			paint.setAntiAlias(true);

			draw_canvas.drawRect(r, paint);
			
			
			
		} else {
			
			Rect r = new Rect(x, y, x + w, y + h);
			paint.setStyle(Paint.Style.FILL);
			paint.setAntiAlias(true);
			//paint.setStrokeWidth(0);
			draw_canvas.drawRect(r, paint);
			
			
			
		}
		
		

	}

	
	protected void draw_circle(Paint paint, int x, int y, int r)
	{
		if(parent_graphics == null && in_map && draw_in_thread)
			drawThreadPool.add_circle(paint, x, y, r);
		else {
			paint.setStyle(Paint.Style.STROKE);
			draw_canvas.drawCircle(x, y, r / 2, paint);
		}
	}


	/** draws a text on the screen
	 *
	 *
	 *
	 * @param x		specifying the x text position
	 * @param y		specifying the y text position
	 * @param text		Text to draw
	 * @param size		specifying the size of the text
	 * @param dx		specifying the dx position, if text is drawn to a line
	 * @param dy		specifying the dy position, if text is drawn to a line
	 * @param bgcolor	specifying the background color
	 * @param lw		specifying the stroke width
	 * @param fgcolor	specifying the color of the text
	 *
	 * @author ?? edit by Sascha Oedekoven (08/2015)
	 **/
	protected void draw_text(int x, int y, String text, int size, int dx, int dy, int bgcolor, int lw, int fgcolor)
	{
		if(parent_graphics == null && in_map && draw_in_thread)
			drawThreadPool.add_text(x,y,text,size,dx,dy,bgcolor, lw, fgcolor);
		else {
			
			//paint.setColor(fgcolor);
			paint.setStrokeWidth(lw);
			Path path=null;

			paint.setTextSize(size / 15);
			paint.setStyle(Paint.Style.FILL);

			if (dx != 0x10000 || dy != 0) {
				path = new Path();
				path.moveTo(x, y);
				path.rLineTo(dx, dy);
				paint.setTextAlign(android.graphics.Paint.Align.LEFT);
			}

			if(bgcolor!=0) {
				paint.setStrokeWidth(3);
				paint.setColor(bgcolor);
				paint.setStyle(Paint.Style.STROKE);
				if(path==null) {
					draw_canvas.drawText(text, x, y, paint);
				} else {
					draw_canvas.drawTextOnPath(text, path, 0, 0, paint);
				}
				paint.setStyle(Paint.Style.FILL);
			}
			
			paint.setColor(fgcolor);

			if(path==null) {
				draw_canvas.drawText(text, x, y, paint);
			} else {
				draw_canvas.drawTextOnPath(text, path, 0, 0, paint);
			}
			paint.clearShadowLayer();
			
			
		}
	}


	/** draws an image on the screen
	 *
	 *
	 *
	 * @param paint		Paint object used to draw the image
	 * @param x		specifying the x position the image is drawn to
	 * @param y		specifying the y position the image is drawn to
	 * @param bitmap	Bitmap object holding the image to draw
	 *
	 * @author ?? edit by Sascha Oedekoven (08/2015)
	 **/
	protected void draw_image(Paint paint, int x, int y, Bitmap bitmap)
	{
		if(parent_graphics == null && in_map && draw_in_thread)
			drawThreadPool.add_image(paint, x, y, bitmap);
		else {
			draw_canvas.drawBitmap(bitmap, x, y, paint);
		}
	}

	/* takes an image and draws it on the screen as a prerendered maptile
	 * 
	 * 
	 * 
	 * @param paint		Paint object used to draw the image
	 * @param count		the number of points specified 
	 * @param p0x and p0y 	specifying the top left point
	 * @param p1x and p1y 	specifying the top right point
	 * @param p2x and p2y 	specifying the bottom left point, not yet used but kept 
	 * 						for compatibility with the linux port
	 * @param bitmap	Bitmap object holding the image to draw
	 * 
	 * TODO make it work with 4 points specified to make it work for 3D mapview, so it can be used
	 * 		for small but very detailed maps as well as for large maps with very little detail but large
	 * 		coverage.
	 * TODO make it work with rectangular tiles as well ?
	 */
	protected void draw_image_warp(Paint paint, int count, int p0x, int p0y, int p1x, int p1y, int p2x, int p2y, Bitmap bitmap)
	{
	
		float width;
		float scale;
		float deltaY;
		float deltaX;
		float angle;
		Matrix matrix;	
	
		if (count == 3)
		{			
			matrix = new Matrix();
			deltaX = p1x - p0x;
			deltaY = p1y - p0y;
			width = (float) (Math.sqrt((deltaX * deltaX) + (deltaY * deltaY)));			
			angle = (float) (Math.atan2(deltaY, deltaX) * 180d / Math.PI);
			scale = width / bitmap.getWidth();
			matrix.preScale(scale, scale);
			matrix.postTranslate(p0x, p0y);			
			matrix.postRotate(angle, p0x, p0y);
			draw_canvas.drawBitmap(bitmap, matrix, paint);
		}
	}

	/* These constants must be synchronized with enum draw_mode_num in graphics.h. */
	public static final int draw_mode_begin = 0;
	public static final int draw_mode_end = 1;
	
	public static final int draw_mode_cachebitmap = 2;
	public static final int draw_mode_drawcache = 3;
	public static final int draw_mode_coords = 4;
	
	public int pos_xxx;	// x Position of the cached bitmap
	public int pos_yyy; // y Position of the cached bitmap
	
	public int no_mode = 0;
	
	/* This function will be called at the start and end of the drawing process.
	 * This functions handles when to draw the bitmap (map) to the screen and when the cached image will be drawn.
	 * 
	 * @param mode	The mode definies which functions will be called:
	 *				mode = draw_mode_begin : The old drawing process will be canceled (if any is still running) and new threads will be started
	 *				mode = draw_mode_end : The bitmap (map) will be shown at the screen
	 *				mode = draw_mode_cachebitmap : The bitmap (map) will be cached
	 *				mode = draw_mode_drawcache : The cached map will be drawn to the correct position
	 *				mode = draw_mode_coords : The following two calls of draw_mode will be the coords of the cached map.
	 * 
	 * @author		edit by Sascha Oedekoven (08/2015)
	 */
	
	protected void draw_mode(int mode)
	{
		
		if(no_mode > 0) {
			
			if(no_mode == 2)
				pos_xxx = mode;	
			else if(no_mode == 1)
				pos_yyy = mode;
			
			no_mode--;
			
		} else if(mode == draw_mode_coords) {
			//next two draw_mode calls are the coords where the buffered image has to be drawn to
			no_mode = 2;
		} else if(mode == draw_mode_cachebitmap) {
			//will be called before post-drawing, to save the map without any buttons

			drawThreadPool.draw_to_screen(mode);
		}else if(mode == draw_mode_drawcache) { 
			
			draw_image(paint, pos_xxx, pos_yyy , cached_bitmap);
			
			
		} else if(parent_graphics == null && in_map && draw_in_thread) {
			
			if (mode == draw_mode_end) {
				drawThreadPool.draw_to_screen(mode);
			} else if(mode == draw_mode_begin && parent_graphics == null) {
				drawThreadPool.cancel_draw();
			}
			
		} else {
			
			if( mode == draw_mode_end ) {
				zoomFactor = 1;
				viewInvalidate();
				
			} else if(in_map && mode == draw_mode_begin && parent_graphics != null) {
				draw_bitmap.eraseColor(0);
			}
		}

	}
	
	protected void draw_drag(int x, int y)
	{
		//Log.e("NavitGraphics","draw_drag");
		pos_x = x;
		pos_y = y;
	}
	protected void overlay_disable(int disable)
	{
		
		// assume we are NOT in map view mode!
		if (parent_graphics == null)
			in_map = (disable==0);
		if (overlay_disabled != disable) {
			overlay_disabled = disable;
			if (parent_graphics != null) {
				//parent_graphics.view.invalidate(get_rect());
				if(in_map && draw_in_thread)
					drawThreadPool.draw_to_screen(2); 
				else
					viewInvalidate();
			}
		}
	}

	protected void overlay_resize(int x, int y, int w, int h, int alpha, int wraparound)
	{
		//Log.e("NavitGraphics","overlay_resize");
		draw_bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		bitmap_w = w;
		bitmap_h = h;
		pos_x = x;
		pos_y = y;
		pos_wraparound = wraparound;
		draw_canvas.setBitmap(draw_bitmap);
	}

	public static String getLocalizedString(String text)
	{
		String ret = CallbackLocalizedString(text);
		//Log.e("NavitGraphics", "callback_handler -> lozalized string=" + ret);
		return ret;
	}




	/**
	 * get localized string
	 */
	public static native String CallbackLocalizedString(String s);





	/* sends the UI-Thread a Message to redraw */


	public void viewInvalidate() {

		if(activity != null)
		activity.runOnUiThread(new Runnable() {

			public void run() {

				if (parent_graphics == null) {
					if(view != null)
						view.invalidate();
				} else {
					if(parent_graphics.view != null)
						parent_graphics.view.invalidate(get_rect());
				}

			}
		});

	}




















}
