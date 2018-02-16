package com.lapharcius.randomstudent;

import android.content.Context;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;

/**
 * Created by Christopher on 2/3/2018.
 */

public class FlingDetector extends GestureDetector{

//    static final int DIRECTION_UNSET = 0;
//    static final int DIRECTION_RIGHT = 1;
//    static final int DIRECTION_LEFT = 2;

    enum flingDirection {DIRECTION_UNSET, DIRECTION_RIGHT, DIRECTION_LEFT}
    static flingDirection currentFlingDirection = flingDirection.DIRECTION_UNSET;

    public FlingDetector(Context base)
    {
        super(base.getApplicationContext(), new OnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return false;
            }

            @Override
            public void onShowPress(MotionEvent e) {

            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {

            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                Log.i("LOGMESSAGE", "User did fling!");
                if (velocityX < 0) {
                    currentFlingDirection = flingDirection.DIRECTION_LEFT;
                }
                else
                {
                    currentFlingDirection = flingDirection.DIRECTION_RIGHT;
                }

                Log.i("LOGMESSAGE", "User did fling! current Direction = " + String.valueOf(currentFlingDirection));
                return false;
//                return onFling(e1, e2, velocityX, velocityY);
            }
        });

    }

    public interface OnGestureDetected {

        /**
         * Called when an item in the navigation menu is selected.
         *
         * @return true to display the item as the selected item
         */
        public boolean onGestureDetected();
    }




}
