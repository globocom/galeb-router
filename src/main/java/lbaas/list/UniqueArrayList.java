package lbaas.list;

import java.util.ArrayList;

public class UniqueArrayList<T> extends ArrayList<T> {

    private static final long serialVersionUID = 250697580015525312L;

    public UniqueArrayList() {
        super();
    }

    public UniqueArrayList(ArrayList<T> aList) {
        this();
        this.addAll(aList);
    }

    @Override
    public boolean add(T object) {
        if (object!=null && !contains(object)) {
            return super.add(object);
        }
        return false;
    }
}
