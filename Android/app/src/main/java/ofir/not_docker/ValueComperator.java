package ofir.not_docker;

import android.bluetooth.BluetoothDevice;

import java.util.Comparator;
import java.util.HashMap;

/**
 * Created by ofir1 on 6/22/2018.
 */


class ValueComparator implements Comparator<BluetoothDevice> {

        HashMap<BluetoothDevice, Integer> map = new HashMap<BluetoothDevice, Integer>();

        public ValueComparator(HashMap<BluetoothDevice, Integer> map){
            this.map.putAll(map);
        }

        @Override
        public int compare(BluetoothDevice s1, BluetoothDevice s2) {
            return map.get(s2).compareTo(map.get(s1));
        }
    }

