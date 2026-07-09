# Detect operating system
ifeq ($(OS),Windows_NT)
    OS_NAME := windows
    EXT := dll
    PLATFORM_INC := win32
    RM := del /q
else
    UNAME_S := $(shell uname -s)
    ifeq ($(UNAME_S),Linux)
        OS_NAME := linux
        EXT := so
        PLATFORM_INC := linux
    endif
    ifeq ($(UNAME_S),Darwin)
        OS_NAME := dylib
        EXT := dylib
        PLATFORM_INC := darwin
    endif
    RM := rm -f
endif

TARGET = libdltest.$(EXT)
CXXFLAGS = -fPIC -g -O2 -I"$(JAVA_HOME)/include" -I"$(JAVA_HOME)/include/$(PLATFORM_INC)"
LDFLAGS = -shared -lpthread

.PHONY: all clean

all: $(TARGET)

$(TARGET): deadlockDetector.o
	$(CXX) $(LDFLAGS) -o $@ $^

%.o: %.cpp
	$(CXX) $(CXXFLAGS) -c $< -o $@

clean:
	$(RM) $(TARGET) *.o