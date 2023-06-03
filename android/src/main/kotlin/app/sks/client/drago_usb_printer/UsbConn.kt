package app.sks.client.drago_usb_printer

import android.hardware.usb.*
import android.os.SystemClock
import app.sks.client.drago_usb_printer.tools.UsbDeviceHelper
import java.util.*


/// Author       : liyufeng
/// Date         : 14:42
/// Description  : 
class UsbConn(private val mUsbDevice: UsbDevice) {

    var isConn = false

    private val mLock = Any()
    private var mConnection: UsbDeviceConnection? = null
    private var mUsbInterface: UsbInterface? = null

    //块传输模式
    private var mBulkEndIn: UsbEndpoint? = null
    private var mBulkEndOut: UsbEndpoint? = null

    //中断传输模式
    private var mInterruptEndIn: UsbEndpoint? = null
    private var mInterruptEndOut: UsbEndpoint? = null

    private fun checkConnAndReConnect(): Boolean {
        if (!isConn) {
            connect()
        }
        return isConn
    }

    fun connect(): Boolean {
        openPort()
        isConn = mBulkEndOut != null && mBulkEndIn != null
        return isConn
    }

    private fun openPort() {
        val count = mUsbDevice.interfaceCount
        var usbInf: UsbInterface? = null
        for (index in 0 until count) {
            val usbInterface = mUsbDevice.getInterface(index)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                usbInf = usbInterface
            }
        }
        usbInf?.let {
            mUsbInterface = usbInf
            mConnection = UsbDeviceHelper.instance.openDevice(mUsbDevice)
            if (!mConnection!!.claimInterface(usbInf, true)) {
                return
            }
            for (i in 0 until usbInf.endpointCount) {
                val ep = usbInf.getEndpoint(i)
                when (ep.type) {
                    UsbConstants.USB_ENDPOINT_XFER_BULK ->
                        //usb 块传输
                        if (ep.direction == UsbConstants.USB_DIR_OUT) {
                            mBulkEndOut = ep
                        } else {
                            mBulkEndIn = ep
                        }
                    UsbConstants.USB_ENDPOINT_XFER_INT -> {
                        //usb 中断传输
                        if (ep.direction == UsbConstants.USB_DIR_OUT) {
                            mInterruptEndOut = ep
                        }
                        if (ep.direction == UsbConstants.USB_DIR_IN) {
                            mInterruptEndIn = ep
                        }
                    }
                }
            }
        }
    }

    fun disconnect(): Boolean {
        synchronized(mLock) {
            if (!isConn) {
                return true
            }
            try {
                mUsbInterface?.let {
                    mConnection?.releaseInterface(it)
                    mConnection?.close()
                }
            } catch (e: Exception) {
                //暂无处理
            } finally {
                mConnection = null
                isConn = false
            }
        }
        return true
    }

    private fun convertVectorByteToBytes(data: Vector<Byte>): ByteArray {
        val sendData = ByteArray(data.size)
        if (data.size > 0) {
            for (i in data.indices) {
                sendData[i] = data[i] as Byte
            }
        }
        return sendData
    }

     fun writeBytes(data: ByteArray): Int {
        if (!checkConnAndReConnect()) {
            return -1
        }
        return mConnection!!.bulkTransfer(mBulkEndOut, data, data.size, 5000)
    }

    fun readBytes(timeOut: Int): ByteArray? {
        if (!checkConnAndReConnect()) {
            throw Exception("printer connect fail")
        }
        val endTime = SystemClock.uptimeMillis() + timeOut.toLong()
        var len: Int = -1
        val buffer = ByteArray(1)
        do {
            if (mBulkEndIn == null) {
                throw Exception("mBulkEndIn is null")
            }
            len = mConnection!!.bulkTransfer(mBulkEndIn, buffer, buffer.size, timeOut)
            if (len > 0) {
                break
            }
            try {
                Thread.sleep(100L)
            } catch (var12: InterruptedException) {
                //暂不处理
            }
        } while (endTime > SystemClock.uptimeMillis())
        return if (len > 0) {
            buffer
        } else {
            null
        }
    }

}