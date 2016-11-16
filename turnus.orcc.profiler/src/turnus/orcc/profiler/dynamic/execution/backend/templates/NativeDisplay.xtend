/* 
 * TURNUS - www.turnus.co
 * 
 * Copyright (C) 2010-2016 EPFL SCI STI MM
 *
 * This file is part of TURNUS.
 *
 * TURNUS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TURNUS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TURNUS.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7
 * 
 * If you modify this Program, or any covered work, by linking or combining it
 * with Eclipse (or a modified version of Eclipse or an Eclipse plugin or 
 * an Eclipse library), containing parts covered by the terms of the 
 * Eclipse Public License (EPL), the licensors of this Program grant you 
 * additional permission to convey the resulting work.  Corresponding Source 
 * for a non-source form of such a combination shall include the source code 
 * for the parts of Eclipse libraries used as well as that of the  covered work.
 * 
 */
package turnus.orcc.profiler.dynamic.execution.backend.templates

import java.util.LinkedHashMap
import java.util.Map
import java.util.Map.Entry
import net.sf.orcc.df.Actor
import net.sf.orcc.df.Network
import net.sf.orcc.ir.Procedure
import net.sf.orcc.ir.Var
import net.sf.orcc.util.FilesManager

/**
 * 
 * @author Endri Bezati
 *
 */
class NativeDisplay extends ExprAndTypePrinter {
	protected Network network
	protected Map<String, Boolean> addtionalNativeDisplayProcs = new LinkedHashMap<String, Boolean>();
	
	new (Network topNetwork) {
		network = topNetwork
	}

	def printDisplay(String targetFolder) {
		val content = getSource
		FilesManager.writeFile(content, targetFolder, "display.cpp")
	}
	
	def getSource(){
		'''
		#include <stdio.h>
		#include <iostream>
		#include <stdlib.h>
		
		#ifndef NO_DISPLAY
		#include <SDL/SDL.h>
		static SDL_Surface *m_screen;
		static SDL_Overlay *m_overlay;
		#else
		#include "timer.h"
		static Timer timer;
		#endif
		#include "get_opt.h"
		
		#ifndef NO_DISPLAY
		static void press_a_key(int code) {
			char buf[2];
			char *ptrBuff = NULL;
		
			printf("Press a key to continue\n");
			ptrBuff=fgets(buf, 2, stdin);
			if(ptrBuff == NULL) {
				fprintf(stderr,"error when using fgets\n");
			}
			exit(code);
		}
		#endif
		
		static unsigned int startTime;
		static unsigned int relativeStartTime;
		static int lastNumPic;
		static int numPicturesDecoded;
		
		void displayYUV_setSize(int width, int height)
		{
		#ifndef NO_DISPLAY
			//std::cout << "set display to " << width << " x " << height << std::endl;
			m_screen = SDL_SetVideoMode(width, height, 0, SDL_HWSURFACE);
			if (m_screen == NULL) {
				fprintf(stderr, "Couldn't set video mode!\n");
				press_a_key(-1);
			}
		
			if (m_overlay != NULL) {
				SDL_FreeYUVOverlay(m_overlay);
			}
		
			m_overlay = SDL_CreateYUVOverlay(width, height, SDL_YV12_OVERLAY, m_screen);
			if (m_overlay == NULL) {
				fprintf(stderr, "Couldn't create overlay: %s\n", SDL_GetError());
				press_a_key(-1);
			}
		#endif
		}
		
		void displayYUV_displayPicture(unsigned char pictureBufferY[], unsigned char pictureBufferU[], unsigned char pictureBufferV[], short pictureWidth, short pictureHeight) 
		{
		#ifndef NO_DISPLAY
			static unsigned short lastWidth = 0;
			static unsigned short lastHeight = 0;
		
			SDL_Rect rect = { 0, 0, pictureWidth, pictureHeight };
		
			SDL_Event event;
		
			if((pictureHeight != lastHeight) || (pictureWidth != lastWidth)) {
				displayYUV_setSize(pictureWidth, pictureHeight);
				lastHeight = pictureHeight;
				lastWidth  = pictureWidth;
			}
			if (SDL_LockYUVOverlay(m_overlay) < 0) {
				fprintf(stderr, "Can't lock screen: %s\n", SDL_GetError());
				press_a_key(-1);
			}
		
			memcpy(m_overlay->pixels[0], pictureBufferY, pictureWidth * pictureHeight );
			memcpy(m_overlay->pixels[1], pictureBufferV, pictureWidth * pictureHeight / 4 );
			memcpy(m_overlay->pixels[2], pictureBufferU, pictureWidth * pictureHeight / 4 );
		
			SDL_UnlockYUVOverlay(m_overlay);
			SDL_DisplayYUVOverlay(m_overlay, &rect);
		
			/* Grab all the events off the queue. */
			while (SDL_PollEvent(&event)) {
				switch (event.type) {
				case SDL_KEYDOWN:
				case SDL_QUIT:
					exit(0);
					break;
				default:
					break;
				}
			}
		#endif
		}
		
		void displayYUV_init()
		{
		#ifndef NO_DISPLAY
			// First, initialize SDL's video subsystem.
			if (SDL_Init( SDL_INIT_VIDEO ) < 0) {
				fprintf(stderr, "Video initialization failed: %s\n", SDL_GetError());
				press_a_key(-1);
			}
		
			SDL_WM_SetCaption("display", NULL);
		
			atexit(SDL_Quit);
		#endif
		}
		
		/**
		 * @brief Return the number of frames the user want to decode before exiting the application.
		 * If user didn't use the -f flag, it returns -1 (DEFAULT_INFINITEà).
		 * @return The
		 */
		int displayYUV_getNbFrames() 
		{
			return nbFrames;
		}
		
		unsigned char displayYUV_getFlags()
		{
			return 3;
		}
		
		int compareYUV_compareComponent(const int x_size, const int y_size, const int x_size_test_img, 
			const unsigned char *true_img_uchar, const unsigned char *test_img_uchar,
			unsigned char SizeMbSide, char Component_Type) 
		{
			return 0;
		}
		
		void compareYUV_init()
		{
		}
		
		void compareYUV_readComponent(unsigned char **Component, unsigned short width, unsigned short height, char sizeChanged)
		{
		}
		
		void compareYUV_comparePicture(unsigned char pictureBufferY[], unsigned char pictureBufferU[],
			unsigned char pictureBufferV[], short pictureWidth,
			short pictureHeight)
		{
		}
		
		
		static void print_fps_avg(void) {
		
		#ifndef NO_DISPLAY
			unsigned int endTime = SDL_GetTicks();
		#else
			unsigned int endTime = timer.getMilliseconds();
		#endif
			printf("%i images in %f seconds: %f FPS\n", numPicturesDecoded,
				(float) (endTime - startTime)/ 1000.0f,
				1000.0f * (float) numPicturesDecoded / (float) (endTime -startTime));
		}
		
		void fpsPrintInit() {
		#ifndef NO_DISPLAY
			startTime = SDL_GetTicks();
		#else
			timer.reset();
			startTime = timer.getMilliseconds();
		#endif
			relativeStartTime = startTime;
			numPicturesDecoded = 0;
			lastNumPic = 0;
			atexit(print_fps_avg);
		}
		
		void fpsPrintNewPicDecoded(void) {
			unsigned int endTime;
			numPicturesDecoded++;
		#ifndef NO_DISPLAY
			endTime = SDL_GetTicks();
		#else
			endTime = timer.getMilliseconds();
		#endif
			if (endTime - relativeStartTime > 5000) {
				printf("%f images/sec\n", 1000.0f * (float) (numPicturesDecoded - lastNumPic) / (float) (endTime - relativeStartTime));
				relativeStartTime = endTime;
				lastNumPic = numPicturesDecoded;
			}
		}
		«printAddtionalNativeDisplayProcs»
		'''
	}

	def printAddtionalNativeDisplayProcs() {
		addtionalNativeDisplayProcs.put("hevcCompareInit", false)
		addtionalNativeDisplayProcs.put("compareYUV_comparePictBuffer", false) 
		addtionalNativeDisplayProcs.put("displayYuvHevc_init", false)
		addtionalNativeDisplayProcs.put("displayYUV_displayPictBuffer", false)
	'''
	«FOR adtlProc : addtionalNativeDisplayProcs.entrySet()»
		«FOR actor : network.children.filter(typeof(Actor))»
			«FOR proc : actor.procs.filter(p | p.native && adtlProc.getKey().equals(p.name))»
				«IF !adtlProc.getValue()»
					«proc.prototypeNativeProc»
					«switch adtlProc.getKey() {
						case "hevcCompareInit" : adtlProc.printBody_hevcCompareInit
						case "compareYUV_comparePictBuffer" : adtlProc.printBody_compareYUV_comparePictBuffer
						case "displayYuvHevc_init" : adtlProc.printBody_displayYuvHevc_init
						case "displayYUV_displayPictBuffer" : adtlProc.printBody_displayYUV_displayPictBuffer
						default : ""				
					}»
				«ENDIF»
			«ENDFOR»
		«ENDFOR»
	«ENDFOR»
	'''
	}

	def protected varDecl(Var v) {
		'''«IF !v.hasAttribute("shared")»«v.type.doSwitch» «v.name»«FOR dim : v.type.dimensions»[«dim»]«ENDFOR»«ENDIF»'''
	}

	def protected prototypeNativeProc(Procedure proc) 
	'''
		
		«proc.returnType.doSwitch» «proc.name» («FOR param : proc.parameters SEPARATOR ", "»«param.variable.varDecl»«ENDFOR») {
	'''

	def protected printBody_hevcCompareInit(Entry<String, Boolean> procEntry) { 
		procEntry.setValue(true)
	'''
		#ifdef USE_COMPARE
		    if (opt->yuv_file == NULL) {
		       return;
		    }
		    if (ptrFile == NULL) {
		        ptrFile = fopen(opt->yuv_file, "rb");
		    }
		    if (ptrFile == NULL) {
		        fprintf(stderr, "could not open file \"%s\"\n", opt->yuv_file);
		        exit(1);
		    }
			useCompare = 1;
			fseek(ptrFile, 0, SEEK_END);
			fileSize = ftell(ptrFile);
			fseek(ptrFile, 0, SEEK_SET);
		#endif
		}
	'''	
	}

	def protected printBody_compareYUV_comparePictBuffer(Entry<String, Boolean> procEntry) { 
		procEntry.setValue(true)
	'''
		#ifdef USE_COMPARE
			static unsigned int frameNumber = 0;
		    static int prevXSize = 0;
		    static int prevYSize = 0;
		
		    static unsigned char *Y = NULL;
		    static unsigned char *U = NULL;
		    static unsigned char *V = NULL;
		
		    char sizeChanged;
		
		    int numErrors = 0;
		
		    printf("Frame number %d", frameNumber);
		    frameNumber++;
		
		    sizeChanged = ((prevXSize*prevYSize) != (pictureWidth*pictureHeight)) ? 1 : 0;
		    compareYUV_readComponent(&Y, pictureWidth,   pictureHeight,   sizeChanged);
		    compareYUV_readComponent(&U, pictureWidth/2, pictureHeight/2, sizeChanged);
		    compareYUV_readComponent(&V, pictureWidth/2, pictureHeight/2, sizeChanged);
		
		    numErrors += compareYUV_compareComponent(pictureWidth, pictureHeight, MAX_PICT_WIDTH+2*BorderSizeLUM, Y,
		    		&pictureBuffer[compIdx][0][BorderSizeLUM][BorderSizeLUM], 4, 'Y');
		    numErrors += compareYUV_compareComponent(pictureWidth >> 1, pictureHeight >> 1, MAX_PICT_WIDTH+2*BorderSizeLUM, U,
		    		&pictureBuffer[compIdx][1][BorderSizeCHR][BorderSizeCHR], 2, 'U');
		    numErrors += compareYUV_compareComponent(pictureWidth >> 1, pictureHeight >> 1, MAX_PICT_WIDTH+2*BorderSizeLUM, V,
		    		&pictureBuffer[compIdx][2][BorderSizeCHR][BorderSizeCHR], 2, 'V');
		
		    if(numErrors == 0)
		    {
		        printf("; no error detected !\n");
		    } else {
		        printf("; %d errors detected !\n", numErrors);
		        compareErrors += numErrors;
		    }
		
		    if(ftell(ptrFile) == fileSize) {
		        rewind(ptrFile);
		        frameNumber = 0;
		    }
		    prevXSize = pictureWidth;
		    prevYSize = pictureHeight;
		#endif
		}
	'''
	}
	
	def protected printBody_displayYuvHevc_init(Entry<String, Boolean> procEntry) { 
		procEntry.setValue(true)
	'''
			displayYUV_init();
		    hevcCompareInit();
		}
	'''
	}

	def protected printBody_displayYUV_displayPictBuffer(Entry<String, Boolean> procEntry) { 
		procEntry.setValue(true)
	'''
		#ifndef NO_DISPLAY
			static unsigned int lastWidth = 0;
		    static unsigned int lastHeight = 0;
		    int y;
		
		    SDL_Rect rect = { 0, 0, pictureWidth, pictureHeight };
		
		    SDL_Event event;
		
		    if ((pictureHeight != lastHeight) || (pictureWidth != lastWidth)) {
		        displayYUV_setSize(pictureWidth, pictureHeight);
		        lastHeight = pictureHeight;
		        lastWidth = pictureWidth;
		    }
		
		    if (SDL_LockYUVOverlay(m_overlay) < 0) {
		        fprintf(stderr, "Can't lock screen: %s\n", SDL_GetError());
		        press_a_key(-1);
		    }
		
		    for(y = 0; y < pictureHeight; y++) {
				memcpy(&m_overlay->pixels[0][y*pictureWidth], &pictureBuffer[dispIdx][0][y + BorderSizeLUM][BorderSizeLUM], pictureWidth);
			}
		
		    for(y = 0; y < pictureHeight / 2; y++) {
				memcpy(&m_overlay->pixels[1][y*pictureWidth / 2], &pictureBuffer[dispIdx][2][y + BorderSizeCHR][BorderSizeCHR], pictureWidth / 2);
				memcpy(&m_overlay->pixels[2][y*pictureWidth / 2], &pictureBuffer[dispIdx][1][y + BorderSizeCHR][BorderSizeCHR], pictureWidth / 2);
		    }
		
		    SDL_UnlockYUVOverlay(m_overlay);
		    SDL_DisplayYUVOverlay(m_overlay, &rect);
		
		    /* Grab all the events off the queue. */
			while (SDL_PollEvent(&event)) {
				switch (event.type) {
				case SDL_KEYDOWN:
				case SDL_QUIT:
					exit(0);
					break;
				default:
					break;
				}
			}
		#endif
		}
	'''
	}
}