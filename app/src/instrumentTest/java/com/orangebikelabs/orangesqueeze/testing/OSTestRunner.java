/*
 * Copyright (c) 2014 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.testing;

import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;

//import org.junit.runners.model.InitializationError;
//import org.robolectric.RobolectricContext;
//import org.robolectric.RobolectricTestRunner;
//
//public class OSTestRunner extends RobolectricTestRunner {
//	public OSTestRunner(Class<?> testClass) throws InitializationError {
//		super(RobolectricContext.bootstrap(OSTestRunner.class, testClass, new OurFactory()));
//	}
//	
//	static class OurFactory implements RobolectricContext.Factory {
//		@Override
//		public RobolectricContext create() {
//            return new RobolectricContext() {
////                @Override
////                protected AndroidManifest createAppManifest() {
////                    return new AndroidManifest(new File("../OSLibrary"));
////                }
//            };
//        }
//	}
//}

public class OSTestRunner extends RobolectricTestRunner {
    public OSTestRunner(Class<?> testClass) throws InitializationError {
        super(testClass);

        // , new File("../OSLibrary")
    }
}
