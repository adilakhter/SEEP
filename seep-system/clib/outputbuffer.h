#ifndef __OUTPUT_BUFFER_H_
#define __OUTPUT_BUFFER_H_

#include <CL/cl.h>

#include <jni.h>

typedef struct output_buffer *outputBufferP;
typedef struct output_buffer {
	int size;
	unsigned char writeOnly;
	cl_mem device_buffer;
	cl_mem pinned_buffer;
	void  *mapped_buffer;
} output_buffer_t;

outputBufferP getOutputBuffer (cl_context, cl_command_queue, void *, int, int);

outputBufferP pinOutputBuffer (cl_context, int);

void freeOutputBuffer (outputBufferP, cl_command_queue);

int getOutputBufferSize (outputBufferP);

#endif /* __OUTPUT_BUFFER_H_ */
