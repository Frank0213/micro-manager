// Mock device adapter for testing of device sequencing
//
// Copyright (C) 2014 University of California, San Francisco.
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by the
// Free Software Foundation.
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
// for more details.
//
// IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this library; if not, write to the Free Software Foundation,
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//
// Author: Mark Tsuchida

#include "SequenceTester.h"
#include "SequenceTesterImpl.h"

#include "ModuleInterface.h"

#include <boost/bind.hpp>
#include <boost/move/move.hpp>
#include <boost/thread.hpp>
#include <exception>
#include <sstream>
#include <string>


// Container for Micro-Manager error code
class DeviceError : public std::exception
{
   const int code_;
public:
   DeviceError(int code) : code_(code) {}
   int GetCode() const { return code_; }
};


MODULE_API void
InitializeModuleData()
{
   RegisterDevice("THub", MM::HubDevice,
         "Fake devices for automated testing");
}


inline bool StartsWith(const std::string& prefix, const std::string& s)
{
   return s.substr(0, prefix.size()) == prefix;
}


MODULE_API MM::Device*
CreateDevice(const char* deviceName)
{
   if (!deviceName)
      return 0;
   const std::string name(deviceName);
   if (name == "THub")
      return new TesterHub(name);

   // By using prefix matching, we can allow the creation of an arbitrary
   // number of each device for testing.
   if (StartsWith("TCamera", name))
      return new TesterCamera(name);
   if (StartsWith("TShutter", name))
      return new TesterShutter(name);
   if (StartsWith("TXYStage", name))
      return new TesterXYStage(name);
   if (StartsWith("TZStage", name))
      return new TesterZStage(name);
   if (StartsWith("TAFStage", name))
      return new TesterAFStage(name);
   if (StartsWith("TAutofocus", name))
      return new TesterAutofocus(name);
   return 0;
}


MODULE_API void
DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


TesterHub::TesterHub(const std::string& name) :
   Super(name)
{
}


int
TesterHub::Initialize()
{
   int err;

   err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


int
TesterHub::Shutdown()
{
   int err;

   err = Super::Shutdown();
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


int
TesterHub::DetectInstalledDevices()
{
   ClearInstalledDevices();
   AddInstalledDevice(new TesterCamera("TCamera-0"));
   AddInstalledDevice(new TesterCamera("TCamera-1"));
   AddInstalledDevice(new TesterShutter("TShutter-0"));
   AddInstalledDevice(new TesterShutter("TShutter-1"));
   AddInstalledDevice(new TesterXYStage("TXYStage-0"));
   AddInstalledDevice(new TesterXYStage("TXYStage-1"));
   AddInstalledDevice(new TesterZStage("TZStage-0"));
   AddInstalledDevice(new TesterZStage("TZStage-1"));
   AddInstalledDevice(new TesterAFStage("TAFStage-0"));
   AddInstalledDevice(new TesterAFStage("TAFStage-1"));
   AddInstalledDevice(new TesterAutofocus("TAutofocus-0"));
   AddInstalledDevice(new TesterAutofocus("TAutofocus-1"));
   return DEVICE_OK;
}


TesterCamera::TesterCamera(const std::string& name) :
   Super(name),
   snapCounter_(0),
   cumulativeSequenceCounter_(0),
   snapImage_(0),
   stopSequence_(true)
{
}


TesterCamera::~TesterCamera()
{
   delete[] snapImage_;
}


int
TesterCamera::Initialize()
{
   int err;

   err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   exposureSetting_ = FloatSetting<Self>::New(GetLogger(), this, "Exposure",
         100.0, true, 0.1, 1000.0);
   binningSetting_ = IntegerSetting<Self>::New(GetLogger(), this, "Binning",
         1, true, 1, 1);

   CreateFloatProperty("Exposure", exposureSetting_);
   CreateIntegerProperty("Binning", binningSetting_);

   return DEVICE_OK;
}


int
TesterCamera::Shutdown()
{
   int err;

   err = Super::Shutdown();
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


int
TesterCamera::SnapImage()
{
   delete[] snapImage_;
   snapImage_ = GenerateLogImage(false, snapCounter_++);

   return DEVICE_OK;
}


const unsigned char*
TesterCamera::GetImageBuffer()
{
   return snapImage_;
}


int
TesterCamera::GetBinning() const
{
   return static_cast<int>(binningSetting_->Get());
}


int
TesterCamera::SetBinning(int binSize)
{
   SettingLogger::GuardType g = GetLogger()->Guard();
   MarkBusy();
   return binningSetting_->Set(binSize);
}


long
TesterCamera::GetImageBufferSize() const
{
   return GetImageWidth() * GetImageHeight() * GetImageBytesPerPixel();
}


unsigned
TesterCamera::GetImageWidth() const
{
   return 128; // TODO
}


unsigned
TesterCamera::GetImageHeight() const
{
   return 128; // TODO
}


void
TesterCamera::SetExposure(double exposureMs)
{
   SettingLogger::GuardType g = GetLogger()->Guard();
   MarkBusy();
   exposureSetting_->Set(exposureMs);
}


double
TesterCamera::GetExposure() const
{
   return exposureSetting_->Get();
}


int
TesterCamera::SetROI(unsigned, unsigned, unsigned, unsigned)
{
   SettingLogger::GuardType g = GetLogger()->Guard();
   MarkBusy();
   return DEVICE_UNSUPPORTED_COMMAND;
}


int
TesterCamera::GetROI(unsigned& x, unsigned& y, unsigned& w, unsigned& h)
{
   // Always full-frame
   x = y = 0;
   w = GetImageWidth();
   h = GetImageHeight();
   return DEVICE_OK;
}


int
TesterCamera::StartSequenceAcquisition(long count, double, bool stopOnOverflow)
{
   return StartSequenceAcquisitionImpl(true, count, stopOnOverflow);
}


int
TesterCamera::StartSequenceAcquisition(double)
{
   return StartSequenceAcquisitionImpl(false, 0, false);
}


int
TesterCamera::StartSequenceAcquisitionImpl(bool finite, long count,
      bool stopOnOverflow)
{
   {
      boost::lock_guard<boost::mutex> lock(sequenceMutex_);
      if (!stopSequence_)
         return DEVICE_ERR;
      stopSequence_ = false;
   }

   GetCoreCallback()->PrepareForAcq(this);

   // Note: boost::packaged_task<void ()> in more recent versions of Boost.
   boost::packaged_task<void> captureTask(
         boost::bind(&TesterCamera::SendSequence, this,
            finite, count, stopOnOverflow));
   sequenceFuture_ = captureTask.get_future();

   boost::thread captureThread(boost::move(captureTask));
   captureThread.detach();

   return DEVICE_OK;
}


int
TesterCamera::StopSequenceAcquisition()
{
   {
      boost::lock_guard<boost::mutex> lock(sequenceMutex_);
      if (stopSequence_)
         return DEVICE_OK;
      stopSequence_ = true;
   }

   // In newer Boost versions: if (sequenceFuture_.valid())
   if (sequenceFuture_.get_state() != boost::future_state::uninitialized)
   {
      try
      {
         sequenceFuture_.get();
      }
      catch (const DeviceError& e)
      {
         sequenceFuture_ = boost::unique_future<void>();
         return e.GetCode();
      }
      sequenceFuture_ = boost::unique_future<void>();
   }

   return GetCoreCallback()->AcqFinished(this, 0);
}


bool
TesterCamera::IsCapturing()
{
   boost::lock_guard<boost::mutex> lock(sequenceMutex_);
   return !stopSequence_;
}


const unsigned char*
TesterCamera::GenerateLogImage(bool isSequenceImage,
      size_t cumulativeCount, size_t localCount)
{
   size_t bufSize = GetImageBufferSize();
   char* bytes = new char[bufSize];

   GetLogger()->PackAndReset(bytes, bufSize,
         GetName(), isSequenceImage, cumulativeCount, localCount);

   return reinterpret_cast<unsigned char*>(bytes);
}


void
TesterCamera::SendSequence(bool finite, long count, bool stopOnOverflow)
{
   MM::Core* core = GetCoreCallback();

   char label[MM::MaxStrLength];
   GetLabel(label);
   Metadata md;
   md.put("Camera", label);
   std::string serializedMD(md.Serialize());

   const unsigned char* bytes = 0;

   // Currently assumed to be constant over device lifetime
   unsigned width = GetImageWidth();
   unsigned height = GetImageHeight();
   unsigned bytesPerPixel = GetImageBytesPerPixel();

   for (long frame = 0; !finite || frame < count; ++frame)
   {
      {
         boost::lock_guard<boost::mutex> lock(sequenceMutex_);
         if (stopSequence_)
            break;
      }

      delete[] bytes;
      bytes = GenerateLogImage(true, cumulativeSequenceCounter_++, frame);

      try
      {
         int err;
         err = core->InsertImage(this, bytes, width, height,
               bytesPerPixel, serializedMD.c_str());

         if (!stopOnOverflow && err == DEVICE_BUFFER_OVERFLOW)
         {
            core->ClearImageBuffer(this);
            err = core->InsertImage(this, bytes, width, height,
                  bytesPerPixel, serializedMD.c_str(), false);
         }

         if (err != DEVICE_OK)
         {
            bool stopped;
            {
               boost::lock_guard<boost::mutex> lock(sequenceMutex_);
               stopped = stopSequence_;
            }
            // If we're stopped already, that could be the reason for the
            // error.
            if (!stopped)
               BOOST_THROW_EXCEPTION(DeviceError(err));
            else
               break;
         }
      }
      catch (...)
      {
         delete[] bytes;
         throw;
      }
   }

   delete[] bytes;
}


int
TesterXYStage::Initialize()
{
   int err;

   err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   xPositionSteps_ = IntegerSetting<Self>::New(GetLogger(), this,
         "XPositionSteps", 0, false);
   yPositionSteps_ = IntegerSetting<Self>::New(GetLogger(), this,
         "YPositionSteps", 0, false);
   home_ = OneShotSetting<Self>::New(GetLogger(), this, "Home");
   stop_ = OneShotSetting<Self>::New(GetLogger(), this, "Stop");
   setOrigin_ = OneShotSetting<Self>::New(GetLogger(), this, "SetOrigin");

   return DEVICE_OK;
}


int
TesterXYStage::SetPositionSteps(long x, long y)
{
   SettingLogger::GuardType g = GetLogger()->Guard();
   MarkBusy();
   int err1 = xPositionSteps_->Set(x);
   int err2 = yPositionSteps_->Set(y);
   if (err1 != DEVICE_OK)
      return err1;
   if (err2 != DEVICE_OK)
      return err2;
   return DEVICE_OK;
}


int
TesterXYStage::GetPositionSteps(long& x, long& y)
{
   int err1 = xPositionSteps_->Get(x);
   int err2 = yPositionSteps_->Get(y);
   if (err1 != DEVICE_OK)
      return err1;
   if (err2 != DEVICE_OK)
      return err2;
   return DEVICE_OK;
}


int
TesterXYStage::Home()
{
   SettingLogger::GuardType g = GetLogger()->Guard();
   MarkBusy();
   return home_->Set();
}


int
TesterXYStage::Stop()
{
   SettingLogger::GuardType g = GetLogger()->Guard();
   MarkBusy();
   return stop_->Set();
}


int
TesterXYStage::SetOrigin()
{
   SettingLogger::GuardType g = GetLogger()->Guard();
   MarkBusy();
   return setOrigin_->Set();
}


int
TesterXYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
   // Not (yet) designed for testing
   xMin = yMin = -10000000;
   xMax = yMax = +10000000;
   return DEVICE_OK;
}


int
TesterXYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
   long x, X, y, Y;
   int err = GetStepLimits(x, X, y, Y);
   xMin = static_cast<double>(x) / stepsPerUm;
   xMax = static_cast<double>(X) / stepsPerUm;
   yMin = static_cast<double>(y) / stepsPerUm;
   yMax = static_cast<double>(Y) / stepsPerUm;
   return err;
}


int
TesterShutter::Initialize()
{
   int err;

   err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   shutterOpen_ = BoolSetting<Self>::New(GetLogger(), this, "ShutterState",
         false);
   CreateOneZeroProperty("State", shutterOpen_);

   return DEVICE_OK;
}


int
TesterShutter::SetOpen(bool open)
{
   SettingLogger::GuardType g = GetLogger()->Guard();
   MarkBusy();
   return shutterOpen_->Set(open);
}


int
TesterShutter::GetOpen(bool& open)
{
   return shutterOpen_->Get(open);
}


int
TesterAutofocus::Initialize()
{
   int err;

   err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   continuousFocusEnabled_ = BoolSetting<Self>::New(GetLogger(), this,
         "ContinuousFocusEnabled", false);

   offset_ = FloatSetting<Self>::New(GetLogger(), this, "Offset", 0.0, false);

   fullFocus_ = OneShotSetting<Self>::New(GetLogger(), this, "FullFocus");
   incrementalFocus_ = OneShotSetting<Self>::New(GetLogger(), this,
         "IncrementalFocus");

   return DEVICE_OK;
}


int
TesterAutofocus::SetContinuousFocusing(bool state)
{
   SettingLogger::GuardType g = GetLogger()->Guard();
   MarkBusy();
   return continuousFocusEnabled_->Set(state);
}


int
TesterAutofocus::GetContinuousFocusing(bool& state)
{
   return continuousFocusEnabled_->Get(state);
}


bool
TesterAutofocus::IsContinuousFocusLocked()
{
   bool enabled = (continuousFocusEnabled_->Get() != 0);
   if (!enabled)
      return false;

   // TODO Use a busy-like mechanism to return true on the second call after
   // enabling continuous focus. For now, always pretend we're locked.
   return true;
}


int
TesterAutofocus::FullFocus()
{
   return fullFocus_->Set();
}


int
TesterAutofocus::IncrementalFocus()
{
   return incrementalFocus_->Set();
}


int
TesterAutofocus::GetLastFocusScore(double& score)
{
   // I don't see any non-dead use of this function except for one mysterious
   // case in acqEngine that might be a bug. For now, return a constant.
   // Returning an error is not an option due to poor assumptions made by
   // existing code.
   score = 0.0;
   return DEVICE_OK;
}


int
TesterAutofocus::GetCurrentFocusScore(double& score)
{
   // This does not appear to be used by any of our non-dead code.
   return DEVICE_UNSUPPORTED_COMMAND;
}


int
TesterAutofocus::GetOffset(double& offset)
{
   return offset_->Get(offset);
}


int
TesterAutofocus::SetOffset(double offset)
{
   SettingLogger::GuardType g = GetLogger()->Guard();
   MarkBusy();
   return offset_->Set(offset);
}